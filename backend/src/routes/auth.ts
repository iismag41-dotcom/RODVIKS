import { Router } from 'express';
import argon2 from 'argon2';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { prisma } from '../db';
import { env } from '../env';
import { validate, validated } from '../middleware/validate';
import { authLimiter } from '../middleware/rateLimit';

export const authRouter = Router();

const registerSchema = z.object({
  email: z.string().email('Invalid email address').max(200),
  username: z.string()
    .min(3, 'Username must be at least 3 characters')
    .max(24, 'Username must be at most 24 characters')
    .regex(/^[a-zA-Z0-9_]+$/, 'Username can only contain letters, digits and underscore'),
  displayName: z.string().min(1).max(50).optional(),
  password: z.string().min(8, 'Password must be at least 8 characters').max(128),
});

const loginSchema = z.object({
  username: z.string().min(1).max(64),
  password: z.string().min(1).max(128),
});

function signToken(userId: string) {
  return jwt.sign({ sub: userId }, env.jwtSecret, { expiresIn: env.jwtExpiresIn } as jwt.SignOptions);
}

function publicUser(u: any) {
  return {
    id: u.id,
    username: u.username,
    email: u.email,
    displayName: u.displayName ?? u.username,
    avatarUrl: u.avatarUrl,
    bio: u.bio,
  };
}

authRouter.post('/register', authLimiter, validate(registerSchema), async (req, res, next) => {
  try {
    const body = validated<typeof registerSchema._type>(req);
    const existing = await prisma.user.findUnique({ where: { username: body.username } });
    if (existing) {
      res.status(409).json({ error: 'Username already taken' });
      return;
    }
    const emailTaken = await prisma.user.findUnique({ where: { email: body.email } });
    if (emailTaken) {
      res.status(409).json({ error: 'Email already registered' });
      return;
    }
    const passwordHash = await argon2.hash(body.password);
    const user = await prisma.user.create({
      data: {
        username: body.username,
        email: body.email,
        displayName: body.displayName ?? body.username,
        passwordHash,
      },
    });
    const token = signToken(user.id);
    res.status(201).json({ token, user: publicUser(user) });
  } catch (err) {
    next(err);
  }
});

authRouter.post('/login', authLimiter, validate(loginSchema), async (req, res, next) => {
  try {
    const body = validated<typeof loginSchema._type>(req);
    const user = await prisma.user.findUnique({ where: { username: body.username } });
    if (!user) {
      res.status(401).json({ error: 'Invalid username or password' });
      return;
    }
    const ok = await argon2.verify(user.passwordHash, body.password);
    if (!ok) {
      res.status(401).json({ error: 'Invalid username or password' });
      return;
    }
    const token = signToken(user.id);
    res.json({ token, user: publicUser(user) });
  } catch (err) {
    next(err);
  }
});

export { publicUser };
