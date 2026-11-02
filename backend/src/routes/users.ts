import { Router } from 'express';
import path from 'path';
import { z } from 'zod';
import { prisma } from '../db';
import { requireAuth } from '../middleware/auth';
import { validate, validated } from '../middleware/validate';
import { publicUser } from './auth';

export const usersRouter = Router();
usersRouter.use(requireAuth);

usersRouter.get('/me', async (req, res, next) => {
  try {
    const user = await prisma.user.findUnique({ where: { id: req.userId } });
    if (!user) {
      res.status(404).json({ error: 'User not found' });
      return;
    }
    res.json(publicUser(user));
  } catch (err) { next(err); }
});

const patchMeSchema = z.object({
  displayName: z.string().min(1).max(50).optional(),
  bio: z.string().max(200).nullable().optional(),
  avatarUrl: z.string().max(500).nullable().optional(),
});

usersRouter.patch('/me', validate(patchMeSchema), async (req, res, next) => {
  try {
    const body = validated<typeof patchMeSchema._type>(req);
    const user = await prisma.user.update({
      where: { id: req.userId },
      data: {
        ...(body.displayName !== undefined && { displayName: body.displayName }),
        ...(body.bio !== undefined && { bio: body.bio }),
        ...(body.avatarUrl !== undefined && { avatarUrl: body.avatarUrl }),
      },
    });
    res.json(publicUser(user));
  } catch (err) { next(err); }
});

// Search users by query
usersRouter.get('/', async (req, res, next) => {
  try {
    const q = (typeof req.query.q === 'string' ? req.query.q : '').trim();
    if (q.length < 2) {
      res.json([]);
      return;
    }
    const users = await prisma.user.findMany({
      where: {
        id: { not: req.userId },
        OR: [
          { username: { contains: q, mode: 'insensitive' } },
          { displayName: { contains: q, mode: 'insensitive' } },
        ],
      },
      take: 20,
      orderBy: { username: 'asc' },
    });
    res.json(users.map(publicUser));
  } catch (err) { next(err); }
});

// GET /users/:id — public profile
usersRouter.get('/:id', async (req, res, next) => {
  try {
    const user = await prisma.user.findUnique({ where: { id: req.params.id } });
    if (!user) {
      res.status(404).json({ error: 'User not found' });
      return;
    }
    res.json(publicUser(user));
  } catch (err) { next(err); }
});
