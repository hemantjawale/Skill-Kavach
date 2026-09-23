import nodemailer from "nodemailer";
import { z } from "zod";
export const emailAddress = z.string().trim().toLowerCase().max(254).email();
export function smtpConfig(env) {
  const host = env.SMTP_HOST || env.EMAIL_HOST;
  const port = Number(env.SMTP_PORT || env.EMAIL_PORT || 587);
  const user = env.SMTP_USER || env.EMAIL_USER;
  const pass = env.SMTP_PASS || env.EMAIL_PASS;
  const from = env.SMTP_FROM || env.EMAIL_FROM || user;
  if (!host || !user || !pass || !from)
    throw new Error(
      "Configure SMTP_HOST, SMTP_USER, SMTP_PASS and SMTP_FROM (EMAIL_* aliases are supported).",
    );
  if (!Number.isInteger(port) || port < 1 || port > 65535)
    throw new Error("Invalid SMTP_PORT.");
  if (/[\r\n]/.test(from)) throw new Error("Invalid SMTP_FROM.");
  const secure = env.SMTP_SECURE ?? env.EMAIL_SECURE;
  if (secure && !["true", "false"].includes(secure))
    throw new Error("SMTP_SECURE must be true or false.");
  return {
    from,
    options: {
      host,
      port,
      secure: secure ? secure === "true" : port === 465,
      requireTLS: true,
      auth: { user, pass },
      tls: { rejectUnauthorized: true, minVersion: "TLSv1.2" },
      connectionTimeout: 10000,
      greetingTimeout: 10000,
      socketTimeout: 15000,
      disableFileAccess: true,
      disableUrlAccess: true,
    },
  };
}
export function createOtpSender(
  env,
  transportFactory = nodemailer.createTransport,
  fetcher = fetch,
) {
  const provider = env.EMAIL_PROVIDER || (env.BREVO_API_KEY ? "brevo" : "smtp");
  if (!["brevo", "smtp"].includes(provider))
    throw new Error("EMAIL_PROVIDER must be brevo or smtp.");
  if (provider === "brevo") {
    if (!env.BREVO_API_KEY?.trim())
      throw new Error("BREVO_API_KEY is required.");
    const sender = {
      email: emailAddress.parse(env.BREVO_SENDER_EMAIL),
      name: env.BREVO_SENDER_NAME || "SurakshaSetu",
    };
    return async (recipient, code) => {
      const to = emailAddress.parse(recipient);
      if (!/^\d{6}$/.test(code)) throw new Error("Invalid verification code.");
      try {
        const response = await fetcher("https://api.brevo.com/v3/smtp/email", {
          method: "POST",
          redirect: "error",
          signal: AbortSignal.timeout(15000),
          headers: {
            "api-key": env.BREVO_API_KEY.trim(),
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({
            sender,
            to: [{ email: to }],
            subject: "Your SurakshaSetu sign-in code",
            textContent: `Your SurakshaSetu verification code is ${code}. It expires in 5 minutes and can be used once. Do not share it. If you did not request it, ignore this email.`,
          }),
        });
        if (response.status !== 201 || !(await response.json()).messageId)
          throw new Error("Provider rejected email");
      } catch {
        const error = new Error(
          "Email delivery is unavailable. Please retry later or contact your administrator.",
        );
        error.status = 503;
        throw error;
      }
    };
  }
  const { from, options } = smtpConfig(env);
  const transport = transportFactory(options);
  return async (recipient, code) => {
    const to = emailAddress.parse(recipient);
    if (!/^\d{6}$/.test(code)) throw new Error("Invalid verification code.");
    try {
      const result = await transport.sendMail({
        from,
        to,
        subject: "Your SurakshaSetu sign-in code",
        text: `Your SurakshaSetu verification code is ${code}. It expires in 5 minutes and can be used once. Do not share it. If you did not request it, ignore this email.`,
      });
      if (
        !result.accepted?.some(
          (address) => String(address).toLowerCase() === to,
        ) ||
        result.rejected?.length
      )
        throw new Error("Recipient rejected");
    } catch {
      const error = new Error(
        "Email delivery is unavailable. Please retry later or contact your administrator.",
      );
      error.status = 503;
      throw error;
    }
  };
}
