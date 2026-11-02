import { Router } from 'express';
import { z } from 'zod';
import { prisma } from '../db';
import { requireAuth } from '../middleware/auth';
import { validate, validated } from '../middleware/validate';

export const chatsRouter = Router();
chatsRouter.use(requireAuth);

async function assertMembership(chatId: string, userId: string) {
  const member = await prisma.chatMember.findUnique({
    where: { chatId_userId: { chatId, userId } },
  });
  return !!member;
}

function chatWithMembersInclude(userId: string) {
  return {
    members: {
      include: {
        user: {
          select: { id: true, username: true, displayName: true, avatarUrl: true, lastSeenAt: true },
        },
      },
    },
    messages: {
      orderBy: { createdAt: 'desc' as const },
      take: 1,
    },
  };
}

function serializeChat(chat: any, meId: string) {
  const other = chat.members.find((m: any) => m.userId !== meId)?.user ?? null;
  const last = chat.messages[0] ?? null;
  return {
    id: chat.id,
    type: chat.type,
    createdAt: chat.createdAt,
    updatedAt: chat.updatedAt,
    peer: other ? {
      id: other.id,
      username: other.username,
      displayName: other.displayName ?? other.username,
      avatarUrl: other.avatarUrl,
      lastSeenAt: other.lastSeenAt,
    } : null,
    lastMessage: last ? {
      id: last.id,
      text: last.text,
      imageUrl: last.imageUrl,
      senderId: last.senderId,
      createdAt: last.createdAt,
    } : null,
  };
}

// GET /chats
chatsRouter.get('/', async (req, res, next) => {
  try {
    const chats = await prisma.chat.findMany({
      where: { members: { some: { userId: req.userId! } } },
      include: chatWithMembersInclude(req.userId!),
      orderBy: { updatedAt: 'desc' },
    });
    res.json(chats.map(c => serializeChat(c, req.userId!)));
  } catch (err) { next(err); }
});

const createChatSchema = z.object({
  peerId: z.string().uuid(),
});

// POST /chats — find or create a direct chat with peerId
chatsRouter.post('/', validate(createChatSchema), async (req, res, next) => {
  try {
    const body = validated<typeof createChatSchema._type>(req);
    const meId = req.userId!;
    if (body.peerId === meId) {
      res.status(400).json({ error: 'Cannot create chat with yourself' });
      return;
    }
    const peer = await prisma.user.findUnique({ where: { id: body.peerId } });
    if (!peer) {
      res.status(404).json({ error: 'User not found' });
      return;
    }
    // find existing direct chat between the two users
    const existing = await prisma.chat.findFirst({
      where: {
        type: 'direct',
        AND: [
          { members: { some: { userId: meId } } },
          { members: { some: { userId: body.peerId } } },
        ],
      },
      include: chatWithMembersInclude(meId),
    });
    if (existing) {
      res.status(200).json(serializeChat(existing, meId));
      return;
    }
    const chat = await prisma.chat.create({
      data: {
        type: 'direct',
        members: {
          create: [
            { userId: meId },
            { userId: body.peerId },
          ],
        },
      },
      include: chatWithMembersInclude(meId),
    });
    res.status(201).json(serializeChat(chat, meId));
  } catch (err) { next(err); }
});

// GET /chats/:id/messages?before=<iso>&limit=
chatsRouter.get('/:id/messages', async (req, res, next) => {
  try {
    const chatId = req.params.id;
    const meId = req.userId!;
    if (!(await assertMembership(chatId, meId))) {
      res.status(403).json({ error: 'Not a member of this chat' });
      return;
    }
    const limit = Math.min(Math.max(parseInt(String(req.query.limit || '50'), 10) || 50, 1), 100);
    const before = req.query.before ? new Date(String(req.query.before)) : undefined;
    const messages = await prisma.message.findMany({
      where: {
        chatId,
        ...(before && !isNaN(before.getTime()) && { createdAt: { lt: before } }),
      },
      include: {
        reads: { select: { userId: true, readAt: true } },
      },
      orderBy: { createdAt: 'desc' },
      take: limit,
    });
    // also mark messages as read for the viewer
    const unreadIds = messages
      .filter(m => m.senderId !== meId && !m.reads.some(r => r.userId === meId))
      .map(m => m.id);
    if (unreadIds.length > 0) {
      await prisma.messageRead.createMany({
        data: unreadIds.map(messageId => ({ messageId, userId: meId })),
        skipDuplicates: true,
      });
      for (const m of messages) {
        if (unreadIds.includes(m.id)) {
          m.reads.push({ userId: meId, readAt: new Date() });
        }
      }
    }
    res.json(messages.reverse().map(serializeMessage));
  } catch (err) { next(err); }
});

function serializeMessage(m: any) {
  return {
    id: m.id,
    chatId: m.chatId,
    senderId: m.senderId,
    text: m.text,
    imageUrl: m.imageUrl,
    createdAt: m.createdAt,
    readBy: m.reads ? m.reads.map((r: any) => r.userId) : [],
  };
}

const sendMessageSchema = z.object({
  text: z.string().min(1).max(4000).optional(),
  imageUrl: z.string().max(500).optional(),
}).refine(v => v.text || v.imageUrl, { message: 'Message must have text or image' });

// POST /chats/:id/messages
chatsRouter.post('/:id/messages', validate(sendMessageSchema), async (req, res, next) => {
  try {
    const chatId = req.params.id;
    const meId = req.userId!;
    if (!(await assertMembership(chatId, meId))) {
      res.status(403).json({ error: 'Not a member of this chat' });
      return;
    }
    const body = validated<any>(req);
    const message = await prisma.message.create({
      data: {
        chatId,
        senderId: meId,
        text: body.text ?? null,
        imageUrl: body.imageUrl ?? null,
      },
    });
    await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });
    res.status(201).json(serializeMessage(message));
  } catch (err) { next(err); }
});

// GET /chats/:id
chatsRouter.get('/:id', async (req, res, next) => {
  try {
    const meId = req.userId!;
    if (!(await assertMembership(req.params.id, meId))) {
      res.status(403).json({ error: 'Not a member of this chat' });
      return;
    }
    const chat = await prisma.chat.findUnique({
      where: { id: req.params.id },
      include: chatWithMembersInclude(meId),
    });
    if (!chat) {
      res.status(404).json({ error: 'Chat not found' });
      return;
    }
    res.json(serializeChat(chat, meId));
  } catch (err) { next(err); }
});
