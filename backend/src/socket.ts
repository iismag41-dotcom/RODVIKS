import type { Server as HttpServer } from 'http';
import { Server as SocketServer } from 'socket.io';
import jwt from 'jsonwebtoken';
import type { JwtPayload } from './types';
import { env } from './env';
import { prisma } from './db';
import { requireMembership } from './chatAccess';

interface SocketData {
  userId: string;
}

export function initSocket(httpServer: HttpServer) {
  const io = new SocketServer(httpServer, {
    cors: {
      origin: env.isProd ? false : '*',
      methods: ['GET', 'POST'],
    },
  });

  // userId -> socket ids
  const online = new Map<string, Set<string>>();

  io.on('connection', (socket) => {
    let userId = '';
    const token = socket.handshake.auth?.token as string | undefined;
    if (!token) {
      socket.emit('auth_error', 'Missing token');
      socket.disconnect(true);
      return;
    }
    try {
      const payload = jwt.verify(token, env.jwtSecret) as JwtPayload;
      userId = payload.sub;
    } catch {
      socket.emit('auth_error', 'Invalid token');
      socket.disconnect(true);
      return;
    }
    (socket.data as SocketData).userId = userId;

    if (!online.has(userId)) online.set(userId, new Set());
    online.get(userId)!.add(socket.id);
    const firstConnection = online.get(userId)!.size === 1;
    if (firstConnection) {
      socket.broadcast.emit('presence', { userId, online: true });
    }

    // join rooms for each chat the user belongs to
    prisma.chatMember.findMany({ where: { userId } }).then(memberships => {
      for (const m of memberships) socket.join(`chat:${m.chatId}`);
    }).catch(err => console.error('join rooms failed', err));

    socket.on('chat:join', async (chatId: string) => {
      if (typeof chatId !== 'string') return;
      const ok = await requireMembership(chatId, userId);
      if (ok) socket.join(`chat:${chatId}`);
    });

    socket.on('typing', async (payload: { chatId: string; typing: boolean }) => {
      if (!payload || typeof payload.chatId !== 'string') return;
      const ok = await requireMembership(payload.chatId, userId);
      if (!ok) return;
      socket.to(`chat:${payload.chatId}`).emit('typing', {
        chatId: payload.chatId,
        userId,
        typing: !!payload.typing,
      });
    });

    socket.on('message:send', async (payload: { chatId: string; text?: string; imageUrl?: string; clientId?: string }, callback?: (resp: any) => void) => {
      try {
        if (!payload || typeof payload.chatId !== 'string') {
          callback?.({ error: 'bad payload' });
          return;
        }
        const ok = await requireMembership(payload.chatId, userId);
        if (!ok) {
          callback?.({ error: 'forbidden' });
          return;
        }
        const text = typeof payload.text === 'string' ? payload.text.trim().slice(0, 4000) : null;
        const imageUrl = typeof payload.imageUrl === 'string' ? payload.imageUrl.slice(0, 500) : null;
        if (!text && !imageUrl) {
          callback?.({ error: 'empty message' });
          return;
        }
        const message = await prisma.message.create({
          data: { chatId: payload.chatId, senderId: userId, text, imageUrl },
        });
        await prisma.chat.update({ where: { id: payload.chatId }, data: { updatedAt: new Date() } });
        const msg = {
          id: message.id,
          chatId: message.chatId,
          senderId: message.senderId,
          text: message.text,
          imageUrl: message.imageUrl,
          createdAt: message.createdAt,
          readBy: [] as string[],
        };
        io.to(`chat:${payload.chatId}`).emit('message:new', { ...msg, clientId: payload.clientId });
        callback?.({ ok: true, message: msg });
      } catch (err) {
        console.error('message:send failed', err);
        callback?.({ error: 'server error' });
      }
    });

    socket.on('messages:read', async (chatId: string) => {
      try {
        if (typeof chatId !== 'string') return;
        const ok = await requireMembership(chatId, userId);
        if (!ok) return;
        const msgs = await prisma.message.findMany({
          where: {
            chatId,
            senderId: { not: userId },
            reads: { none: { userId } },
          },
          select: { id: true },
          take: 200,
        });
        if (msgs.length > 0) {
          await prisma.messageRead.createMany({
            data: msgs.map(m => ({ messageId: m.id, userId })),
            skipDuplicates: true,
          });
          socket.to(`chat:${chatId}`).emit('messages:read', { chatId, userId, messageIds: msgs.map(m => m.id) });
        }
      } catch (err) {
        console.error('messages:read failed', err);
      }
    });

    socket.on('disconnect', () => {
      const set = online.get(userId);
      if (set) {
        set.delete(socket.id);
        if (set.size === 0) {
          online.delete(userId);
          socket.broadcast.emit('presence', { userId, online: false });
        }
      }
      prisma.user.update({ where: { id: userId }, data: { lastSeenAt: new Date() } }).catch(() => {});
    });
  });

  return { io, online };
}
