import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

export function createOtpSender(env, fetcher = fetch) {
  const provider =
    env.SMS_PROVIDER || (env.SMS_WEBHOOK_URL ? "webhook" : "local");
  if (!["local", "twilio", "webhook"].includes(provider))
    throw new Error("SMS_PROVIDER must be local, twilio, or webhook.");
  if (env.NODE_ENV === "production" && provider === "local")
    throw new Error("Local OTP delivery is disabled in production.");
  if (
    provider === "webhook" &&
    (!env.SMS_WEBHOOK_URL?.startsWith("https://") || !env.SMS_WEBHOOK_TOKEN)
  )
    throw new Error("Webhook delivery requires an HTTPS URL and bearer token.");
  if (
    provider === "twilio" &&
    (!/^AC[0-9a-f]{32}$/i.test(env.TWILIO_ACCOUNT_SID ?? "") ||
      !env.TWILIO_AUTH_TOKEN ||
      (!env.TWILIO_FROM && !env.TWILIO_MESSAGING_SERVICE_SID))
  )
    throw new Error("Twilio account SID, auth token and sender are required.");
  return async (phone, code, challengeId) => {
    if (provider === "local") {
      const path = resolve(env.DEV_OTP_FILE || ".data/otp.json");
      await mkdir(dirname(path), { recursive: true });
      await writeFile(path, JSON.stringify({ challengeId, code }), {
        mode: 0o600,
      });
      return;
    }
    let response;
    if (provider === "twilio") {
      const body = new URLSearchParams({
        To: phone,
        Body: `Your SurakshaSetu verification code is ${code}. It expires in 5 minutes. Do not share this code.`,
      });
      if (env.TWILIO_MESSAGING_SERVICE_SID)
        body.set("MessagingServiceSid", env.TWILIO_MESSAGING_SERVICE_SID);
      else body.set("From", env.TWILIO_FROM);
      response = await fetcher(
        `https://api.twilio.com/2010-04-01/Accounts/${env.TWILIO_ACCOUNT_SID}/Messages.json`,
        {
          method: "POST",
          headers: {
            Authorization: `Basic ${Buffer.from(`${env.TWILIO_ACCOUNT_SID}:${env.TWILIO_AUTH_TOKEN}`).toString("base64")}`,
            "Content-Type": "application/x-www-form-urlencoded",
          },
          body,
          signal: AbortSignal.timeout(10000),
        },
      );
    } else
      response = await fetcher(env.SMS_WEBHOOK_URL, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.SMS_WEBHOOK_TOKEN}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ phone, code, expiresInSeconds: 300 }),
        signal: AbortSignal.timeout(10000),
      });
    if (!response.ok)
      throw new Error(
        "SMS provider rejected delivery. Check provider configuration and delivery logs.",
      );
  };
}
