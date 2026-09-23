import { test } from "node:test";
import assert from "node:assert/strict";
import { createOtpSender } from "../src/sms.js";

test("production cannot start with local OTP delivery", () => {
  assert.throws(
    () => createOtpSender({ NODE_ENV: "production", SMS_PROVIDER: "local" }),
    /disabled/,
  );
});
/*
// --- COMMENTED OUT TWILIO TEST PER REQUEST ---
// test("Twilio delivery uses correct endpoint, encoded body and authenticated sender", async () => { ... });
*/
test("email delivery requires host and configuration", () => {
  assert.throws(
    () => createOtpSender({ OTP_PROVIDER: "email" }),
    /Email OTP delivery requires EMAIL_HOST/,
  );
});
test("webhook rejects insecure transport and propagates provider failures", async () => {
  assert.throws(
    () =>
      createOtpSender({
        SMS_PROVIDER: "webhook",
        SMS_WEBHOOK_URL: "http://provider.example",
        SMS_WEBHOOK_TOKEN: "test",
      }),
    /HTTPS/,
  );
  const sender = createOtpSender(
    {
      SMS_PROVIDER: "webhook",
      SMS_WEBHOOK_URL: "https://provider.example/send",
      SMS_WEBHOOK_TOKEN: "test",
    },
    async () => ({ ok: false }),
  );
  await assert.rejects(
    sender("+919000000001", "123456", "challenge"),
    /rejected delivery/,
  );
});
