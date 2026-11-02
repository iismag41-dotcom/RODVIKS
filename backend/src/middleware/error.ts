import type { Request, Response, NextFunction } from 'express';

export function notFoundHandler(_req: Request, res: Response) {
  res.status(404).json({ error: 'Not found' });
}

export function errorHandler(err: any, _req: Request, res: Response, _next: NextFunction) {
  console.error(err);
  if (err?.code === 'LIMIT_FILE_SIZE' || err?.name === 'PayloadTooLargeError') {
    res.status(413).json({ error: 'File too large' });
    return;
  }
  res.status(err?.status || 500).json({ error: err?.message || 'Internal server error' });
}
