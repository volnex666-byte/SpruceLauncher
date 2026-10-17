import nodemailer from 'nodemailer';
import { config } from './config.mjs';

let transport = null;

if (config.smtp.host) {
  transport = nodemailer.createTransport({
    host: config.smtp.host,
    port: config.smtp.port,
    secure: config.smtp.port === 465,
    auth: config.smtp.user ? { user: config.smtp.user, pass: config.smtp.pass } : undefined,
  });
}

export async function sendOtpEmail(email, code) {
  const minutes = Math.round(config.otpTtlSec / 60);
  if (!transport) {
    console.log(`[mail:dev] OTP для ${email}: ${code} (действует ${minutes} мин)`);
    return;
  }
  await transport.sendMail({
    from: config.smtp.from,
    to: email,
    subject: 'Код входа в SpruceLauncher',
    text: [
      `Ваш код входа: ${code}`,
      '',
      `Код действует ${minutes} минут и может быть использован один раз.`,
      'Если вы не запрашивали вход — просто проигнорируйте это письмо.',
    ].join('\n'),
  });
}
