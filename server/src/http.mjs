export const ah = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

export const parse = (schema, data) => schema.parse(data);
