import type { Request, Response, NextFunction } from 'express';
import { ZodError, type ZodSchema } from 'zod';

export function validate(schema: ZodSchema) {
  return (req: Request, res: Response, next: NextFunction) => {
    try {
      const data = schema.parse(req.body);
      (req as any).validatedBody = data;
      next();
    } catch (err) {
      if (err instanceof ZodError) {
        res.status(400).json({
          error: 'Validation failed',
          details: err.errors.map(e => `${e.path.join('.')}: ${e.message}`),
        });
        return;
      }
      next(err);
    }
  };
}

export function validated<T = any>(req: Request): T {
  return (req as any).validatedBody as T;
}
