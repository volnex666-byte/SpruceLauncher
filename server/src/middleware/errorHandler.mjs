import { ZodError } from 'zod';
import { AppError } from '../errors.mjs';

export function notFoundHandler(req, res) {
  res.status(404).json({ error: { code: 'not_found', message: 'Маршрут не найден' } });
}

export function errorHandler(err, req, res, next) {
  void next;
  if (err instanceof ZodError) {
    return res.status(400).json({
      error: {
        code: 'validation',
        message: 'Некорректные данные',
        details: err.issues.map((issue) => ({
          path: issue.path.join('.'),
          message: issue.message,
        })),
      },
    });
  }
  if (err instanceof AppError) {
    return res.status(err.status).json({
      error: { code: err.code, message: err.message, details: err.details },
    });
  }
  if (err?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: { code: 'bad_json', message: 'Некорректный JSON' } });
  }
  console.error('[error]', err);
  return res.status(500).json({ error: { code: 'internal', message: 'Внутренняя ошибка сервера' } });
}
