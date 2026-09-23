import nodemailer from "nodemailer";
import { smtpConfig } from "../src/email.js";
let transport;
try {
  transport = nodemailer.createTransport(smtpConfig(process.env).options);
  await transport.verify();
  console.log(
    "SMTP connection, TLS and authentication verified. No email was sent; inbox delivery is not verified.",
  );
} catch (e) {
  console.error(
    "SMTP verification failed:",
    /^[A-Z0-9_]+$/.test(e.code || "")
      ? e.code
      : "configuration or connection error",
  );
  process.exitCode = 1;
} finally {
  transport?.close();
}
