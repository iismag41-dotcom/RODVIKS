import { Router } from 'express';
import argon2 from 'argon2';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { prisma } from '../db';
import { env } from '../env';
import { validate, validated } from '../middleware/validate';
import { authLimiter } from '../middleware/rateLimit';
import { requireAuth } from '../middleware/auth';
import { publicUser } from './auth';

export const phoneAuthRouter = Router();

function signToken(userId: string) {
  return jwt.sign({ sub: userId }, env.jwtSecret, { expiresIn: env.jwtExpiresIn } as jwt.SignOptions);
}

function normalizePhone(raw: string): string | null {
  // accepts +7XXXXXXXXXX / 8XXXXXXXXXX / 7XXXXXXXXXX
  let digits = raw.replace(/\D+/g, '');
  if (digits.length === 11 && (digits.startsWith('8') || digits.startsWith('7'))) {
    digits = '7' + digits.slice(1);
  }
  if (digits.length === 10) digits = '7' + digits;
  if (digits.length !== 11 || !digits.startsWith('7')) return null;
  return '+' + digits;
}

// POST /auth/phone/register
const registerSchema = z.object({
  phone: z.string().min(10).max(20),
  password: z.string().min(8, 'Пароль минимум 8 символов').max(128),
});

phoneAuthRouter.post('/register', authLimiter, validate(registerSchema), async (req, res, next) => {
  try {
    const body = validated<{ phone: string; password: string }>(req);
    const phone = normalizePhone(body.phone);
    if (!phone) {
      res.status(400).json({ error: 'Некорректный номер телефона' });
      return;
    }
    const existing = await prisma.user.findUnique({ where: { phone } });
    if (existing) {
      // same generic message as login: do not reveal whether the phone exists
      res.status(409).json({ error: 'Номер уже зарегистрирован' });
      return;
    }
    const passwordHash = await argon2.hash(body.password);
    const user = await prisma.user.create({
      data: {
        phone,
        username: `user_${crypto.randomBytes(6).toString('hex')}`,
        passwordHash,
        displayName: null,
      },
    });
    const token = signToken(user.id);
    // needsUsername: the client prompts for a username on first login
    res.status(201).json({ token, user: publicUser(user), needsUsername: true });
  } catch (err) {
    next(err);
  }
});

// POST /auth/phone/login
const loginSchema = z.object({
  phone: z.string().min(10).max(20),
  password: z.string().min(1).max(128),
});

phoneAuthRouter.post('/login', authLimiter, validate(loginSchema), async (req, res, next) => {
  try {
    const body = validated<{ phone: string; password: string }>(req);
    const phone = normalizePhone(body.phone);
    if (!phone) {
      // same generic error for bad format, unknown phone and wrong password
      res.status(401).json({ error: 'Неверный номер или пароль' });
      return;
    }
    const user = await prisma.user.findUnique({ where: { phone } });
    // constant-shape response: unknown phone and wrong password are indistinguishable
    const hash = user?.passwordHash ?? (await getDummyHash());
    const ok = await argon2.verify(hash, body.password).catch(() => false);
    if (!user || !ok) {
      res.status(401).json({ error: 'Неверный номер или пароль' });
      return;
    }
    const token = signToken(user.id);
    res.json({
      token,
      user: publicUser(user),
      needsUsername: !user.displayName,
    });
  } catch (err) {
    next(err);
  }
});

// A lazily-created argon2 hash of a random string: verifying against it when
// the user does not exist keeps the response time close to the real-user case,
// so timing cannot easily reveal whether a phone number is registered.
let dummyHashPromise: Promise<string> | null = null;
function getDummyHash(): Promise<string> {
  if (!dummyHashPromise) {
    dummyHashPromise = argon2.hash(crypto.randomBytes(32).toString('hex')).catch(() => '');
  }
  return dummyHashPromise;
}

// POST /auth/phone/username  (requires the session token from register/login)
const usernameSchema = z.object({
  username: z.string()
    .min(3, 'Имя пользователя минимум 3 символа')
    .max(24, 'Имя пользователя максимум 24 символа')
    .regex(/^[a-zA-Z0-9_]+$/, 'Только латиница, цифры и подчёркивание'),
  displayName: z.string().min(1).max(50).optional(),
});

phoneAuthRouter.post('/username', requireAuth, authLimiter, validate(usernameSchema), async (req, res, next) => {
  try {
    const body = validated<{ username: string; displayName?: string }>(req);
    const userId = req.userId;
    if (!userId) {
      res.status(401).json({ error: 'Unauthorized' });
      return;
    }
    const taken = await prisma.user.findUnique({ where: { username: body.username } });
    if (taken && taken.id !== userId) {
      res.status(409).json({ error: 'Имя занято' });
      return;
    }
    const user = await prisma.user.update({
      where: { id: userId },
      data: {
        username: body.username,
        displayName: body.displayName ?? body.username,
      },
    });
    res.json({ user: publicUser(user) });
  } catch (err) {
    next(err);
  }
});

export { normalizePhone };
