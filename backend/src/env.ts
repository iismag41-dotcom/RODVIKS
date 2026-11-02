import dotenv from 'dotenv';
dotenv.config();

export const env = {
  port: parseInt(process.env.PORT || '3000', 10),
  nodeEnv: process.env.NODE_ENV || 'development',
  jwtSecret: process.env.JWT_SECRET || '',
  jwtExpiresIn: (process.env.JWT_EXPIRES_IN || '7d') as any,
  maxUploadBytes: (parseInt(process.env.MAX_UPLOAD_MB || '10', 10)) * 1024 * 1024,
  isProd: (process.env.NODE_ENV || 'development') === 'production',
};

if (!env.jwtSecret || env.jwtSecret === 'change-me-to-a-long-random-string') {
  // In dev we generate a fallback so the server can still boot;
  // production requires a real secret.
  if (env.isProd) {
    throw new Error('JWT_SECRET must be set in production');
  }
  env.jwtSecret = 'dev-only-insecure-secret';
}
