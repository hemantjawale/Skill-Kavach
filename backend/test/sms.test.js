import { test } from "node:test";
import assert from "node:assert/strict";
import { createOtpSender } from "../src/sms.js";

test("production cannot start with local OTP delivery", () => {
  assert.throws(
    () => createOtpSender({ NODE_ENV: "production", SMS_PROVIDER: "local" }),
    /disabled/,
  );
});
test("Twilio delivery uses correct endpoint, encoded body and authenticated sender", async () => {
  let sent;
  const sender = createOtpSender(
    {
      SMS_PROVIDER: "twilio",
      TWILIO_ACCOUNT_SID: "AC" + "a".repeat(32),
      TWILIO_AUTH_TOKEN: "test-only-secret",
      TWILIO_FROM: "+15550000001",
    },
    async (url, options) => {
      sent = { url, options };
      return { ok: true };
    },
  );
  await sender("+919000000001", "123456", "test-challenge");
  assert.equal(
    sent.url,
    "https://api.twilio.com/2010-04-01/Accounts/AC" +
      "a".repeat(32) +
      "/Messages.json",
  );
  assert.equal(sent.options.body.get("To"), "+919000000001");
  assert.equal(sent.options.body.get("From"), "+15550000001");
  assert.ok(sent.options.body.get("Body").includes("123456"));
  assert.ok(sent.options.headers.Authorization.startsWith("Basic "));
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
