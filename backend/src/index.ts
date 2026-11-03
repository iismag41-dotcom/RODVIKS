import http from 'http';
import express from 'express';
import cors from 'cors';
import { env } from './env';
import { authRouter } from './routes/auth';
import { phoneAuthRouter } from './routes/phoneAuth';
import { usersRouter } from './routes/users';
import { chatsRouter } from './routes/chats';
import { uploadsRouter } from './routes/uploads';
import { apiLimiter } from './middleware/rateLimit';
import { notFoundHandler, errorHandler } from './middleware/error';
import { initSocket } from './socket';

const app = express();
const httpServer = http.createServer(app);

app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use('/uploads', express.static('uploads'));

app.use('/auth', authRouter);
app.use('/auth/phone', phoneAuthRouter);
app.use('/users', apiLimiter, usersRouter);
app.use('/chats', apiLimiter, chatsRouter);
app.use('/media', apiLimiter, uploadsRouter);

app.get('/health', (_req, res) => res.json({ ok: true }));

app.use(notFoundHandler);
app.use(errorHandler);

initSocket(httpServer);

httpServer.listen(env.port, () => {
  console.log(`RODVIKS backend listening on http://localhost:${env.port}`);
});
