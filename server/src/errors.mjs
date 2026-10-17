export class AppError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export const badRequest = (code, message) => new AppError(400, code, message);
export const unauthorized = (code = 'unauthorized', message = 'Требуется повторный вход') =>
  new AppError(401, code, message);
export const forbidden = (code = 'forbidden', message = 'Доступ запрещён') =>
  new AppError(403, code, message);
export const notFound = (code = 'not_found', message = 'Не найдено') =>
  new AppError(404, code, message);
export const conflict = (code, message) => new AppError(409, code, message);
export const tooMany = (message = 'Слишком много запросов, попробуйте позже') =>
  new AppError(429, 'rate_limited', message);
