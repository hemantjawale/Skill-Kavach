import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import nodemailer from "nodemailer";

export function createOtpSender(env, fetcher = fetch) {
  // Supported OTP providers: "email" (or "smtp"), "local", "webhook"
  // (Twilio code commented out per user instruction)
  const provider =
    env.OTP_PROVIDER || env.SMS_PROVIDER || (env.EMAIL_HOST || env.SMTP_HOST ? "email" : (env.SMS_WEBHOOK_URL ? "webhook" : "local"));

  if (!["local", "email", "smtp", "webhook", "twilio"].includes(provider))
    throw new Error("OTP_PROVIDER must be email, local, webhook, or smtp.");

  if (env.NODE_ENV === "production" && provider === "local")
    throw new Error("Local OTP delivery is disabled in production.");

  if (
    provider === "webhook" &&
    (!env.SMS_WEBHOOK_URL?.startsWith("https://") || !env.SMS_WEBHOOK_TOKEN)
  )
    throw new Error("Webhook delivery requires an HTTPS URL and bearer token.");

  if (
    (provider === "email" || provider === "smtp") &&
    (!env.EMAIL_HOST && !env.SMTP_HOST)
  )
    throw new Error("Email OTP delivery requires EMAIL_HOST (or SMTP_HOST), EMAIL_USER, and EMAIL_PASS configuration.");

  /*
  // --- COMMENTED OUT TWILIO CODE PER REQUEST ---
  // if (
  //   provider === "twilio" &&
  //   (!/^AC[0-9a-f]{32}$/i.test(env.TWILIO_ACCOUNT_SID ?? "") ||
  //     !env.TWILIO_AUTH_TOKEN ||
  //     (!env.TWILIO_FROM && !env.TWILIO_MESSAGING_SERVICE_SID))
  // )
  //   throw new Error("Twilio account SID, auth token and sender are required.");
  // ---------------------------------------------
  */

  let mailTransporter = null;
  if (provider === "email" || provider === "smtp") {
    const host = env.EMAIL_HOST || env.SMTP_HOST;
    const port = Number(env.EMAIL_PORT || env.SMTP_PORT || 587);
    const user = env.EMAIL_USER || env.SMTP_USER;
    const pass = env.EMAIL_PASS || env.SMTP_PASS;

    mailTransporter = nodemailer.createTransport({
      host,
      port,
      secure: port === 465,
      auth: user ? { user, pass } : undefined,
    });
  }

  return async (targetIdentity, code, challengeId) => {
    if (provider === "local") {
      const path = resolve(env.DEV_OTP_FILE || ".data/otp.json");
      await mkdir(dirname(path), { recursive: true });
      await writeFile(path, JSON.stringify({ challengeId, code }), {
        mode: 0o600,
      });
      return;
    }

    if (provider === "email" || provider === "smtp") {
      const from = env.EMAIL_FROM || env.SMTP_FROM || '"SurakshaSetu Safety" <no-reply@surakshasetu.org>';
      const recipient = (targetIdentity && typeof targetIdentity === "string" && targetIdentity.includes("@"))
        ? targetIdentity
        : (env.EMAIL_USER || "jatinbhosale428@gmail.com");
      await mailTransporter.sendMail({
        from,
        to: recipient,
        subject: `Your SurakshaSetu Verification Code: ${code}`,
        text: `Your SurakshaSetu safety platform verification code is ${code}.\n\nIt expires in 5 minutes. Do not share this code with anyone.`,
        html: `
          <div style="font-family: sans-serif; padding: 20px; color: #20242C;">
            <h2>SurakshaSetu Verification Code</h2>
            <p>Use the following 6-digit code to sign into your safety training account:</p>
            <h1 style="font-size: 32px; letter-spacing: 4px; color: #586DAF;">${code}</h1>
            <p>This code expires in <strong>5 minutes</strong>. Do not share this code.</p>
          </div>
        `,
      });
      return;
    }

    /*
    // --- COMMENTED OUT TWILIO SEND CODE PER REQUEST ---
    // if (provider === "twilio") {
    //   const body = new URLSearchParams({
    //     To: targetIdentity,
    //     Body: `Your SurakshaSetu verification code is ${code}. It expires in 5 minutes. Do not share this code.`,
    //   });
    //   if (env.TWILIO_MESSAGING_SERVICE_SID)
    //     body.set("MessagingServiceSid", env.TWILIO_MESSAGING_SERVICE_SID);
    //   else body.set("From", env.TWILIO_FROM);
    //   response = await fetcher(
    //     `https://api.twilio.com/2010-04-01/Accounts/${env.TWILIO_ACCOUNT_SID}/Messages.json`,
    //     {
    //       method: "POST",
    //       headers: {
    //         Authorization: `Basic ${Buffer.from(`${env.TWILIO_ACCOUNT_SID}:${env.TWILIO_AUTH_TOKEN}`).toString("base64")}`,
    //         "Content-Type": "application/x-www-form-urlencoded",
    //       },
    //       body,
    //       signal: AbortSignal.timeout(10000),
    //     },
    //   );
    // }
    // ---------------------------------------------------
    */

    if (provider === "webhook") {
      const response = await fetcher(env.SMS_WEBHOOK_URL, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.SMS_WEBHOOK_TOKEN}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ phone: targetIdentity, code, expiresInSeconds: 300 }),
        signal: AbortSignal.timeout(10000),
      });

      if (!response.ok)
        throw new Error(
          "OTP provider rejected delivery. Check provider configuration and delivery logs.",
        );
    }
  };
}
