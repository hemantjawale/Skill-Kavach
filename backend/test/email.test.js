import { test } from "node:test";
import assert from "node:assert/strict";
import { createOtpSender, smtpConfig } from "../src/email.js";
const env = {
  SMTP_HOST: "smtp.example.test",
  SMTP_PORT: "587",
  SMTP_USER: "sender@example.test",
  SMTP_PASS: "test-secret",
  SMTP_FROM: "Safety <sender@example.test>",
};
test("SMTP requires TLS, bounded timeouts and complete credentials", () => {
  const { options } = smtpConfig(env);
  assert.equal(options.requireTLS, true);
  assert.equal(options.tls.rejectUnauthorized, true);
  assert.equal(options.secure, false);
  assert.equal(smtpConfig({ ...env, SMTP_PORT: "465" }).options.secure, true);
  assert.throws(() => smtpConfig({ ...env, SMTP_PASS: "" }));
  assert.throws(() => smtpConfig({ ...env, SMTP_PORT: "bad" }));
  assert.throws(() =>
    smtpConfig({ ...env, SMTP_FROM: "bad\r\nBcc: bad@example.test" }),
  );
});
test("SMTP sends only to the requested registered email with no fallback", async () => {
  let sent;
  const sender = createOtpSender(env, () => ({
    sendMail: async (message) => {
      sent = message;
      return { accepted: [message.to], rejected: [] };
    },
  }));
  await sender(" Employee@Example.test ", "123456");
  assert.equal(sent.to, "employee@example.test");
  assert.match(sent.text, /123456/);
  assert.ok(!sent.subject.includes("123456"));
  await assert.rejects(sender("+919000000001", "123456"));
  await assert.rejects(sender(undefined, "123456"));
});
test("SMTP errors and rejected recipients are sanitized", async () => {
  const rejected = createOtpSender(env, () => ({
    sendMail: async () => ({
      accepted: [],
      rejected: ["employee@example.test"],
    }),
  }));
  await assert.rejects(
    rejected("employee@example.test", "123456"),
    (e) => e.status === 503,
  );
  const failed = createOtpSender(env, () => ({
    sendMail: async () => {
      throw Error("secret internal credentials");
    },
  }));
  await assert.rejects(
    failed("employee@example.test", "123456"),
    (e) => e.status === 503 && !e.message.includes("secret"),
  );
});
test("existing EMAIL aliases are supported", () => {
  const options = smtpConfig({
    EMAIL_HOST: env.SMTP_HOST,
    EMAIL_USER: env.SMTP_USER,
    EMAIL_PASS: env.SMTP_PASS,
    EMAIL_FROM: env.SMTP_FROM,
    EMAIL_PORT: "587",
  });
  assert.equal(options.options.host, env.SMTP_HOST);
});

test("Brevo sends authenticated HTTPS email to only the matched recipient without SMTP", async () => {
  let sent;
  const sender = createOtpSender(
    {
      EMAIL_PROVIDER: "brevo",
      BREVO_API_KEY: "test-key",
      BREVO_SENDER_EMAIL: "sender@example.test",
    },
    () => {
      throw Error("SMTP must not be used");
    },
    async (url, options) => {
      sent = { url, options };
      return { status: 201, json: async () => ({ messageId: "test-id" }) };
    },
  );
  await sender("worker@example.test", "123456");
  assert.equal(sent.url, "https://api.brevo.com/v3/smtp/email");
  assert.equal(sent.options.headers["api-key"], "test-key");
  assert.equal(sent.options.redirect, "error");
  assert.deepEqual(JSON.parse(sent.options.body).to, [
    { email: "worker@example.test" },
  ]);
  assert.match(JSON.parse(sent.options.body).textContent, /123456/);
});
test("Brevo fails closed for missing configuration and provider failures", async () => {
  assert.throws(() => createOtpSender({ EMAIL_PROVIDER: "brevo" }));
  assert.throws(() => createOtpSender({ EMAIL_PROVIDER: "unknown" }));
  for (const status of [401, 429, 500]) {
    const sender = createOtpSender(
      { BREVO_API_KEY: "test", BREVO_SENDER_EMAIL: "sender@example.test" },
      undefined,
      async () => ({ status }),
    );
    await assert.rejects(
      sender("worker@example.test", "123456"),
      (e) => e.status === 503,
    );
  }
});
