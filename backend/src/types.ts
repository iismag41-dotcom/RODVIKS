import type { Request } from 'express';

export interface JwtPayload {
  sub: string; // user id
}

declare module 'express-serve-static-core' {
  interface Request {
    userId?: string;
  }
}
