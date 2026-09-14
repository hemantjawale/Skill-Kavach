import { resolve } from "node:path";
import { database } from "./db.js";
import { createApp } from "./app.js";
import { startPushWorker } from "./push.js";
import { createOtpSender } from "./sms.js";
import { runtimeConfig } from "./config.js";
const config = runtimeConfig(process.env);
const db = database(process.env.DATABASE_URL);
const app = createApp(db, {
  proxyHops: config.proxyHops,
  jwtSecret: process.env.JWT_SECRET,
  otpPepper: process.env.OTP_PEPPER,
  publicUrl: config.publicUrl,
  adminPath: resolve("../admin/dist"),
  sendOtp: createOtpSender(process.env),
});
const stopPush = startPushWorker(db, process.env.FIREBASE_PROJECT_ID);
const server = app.listen(config.port, "0.0.0.0", () =>
  console.log("SurakshaSetu API is listening"),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () =>
    server.close(async () => {
      stopPush();
      await db.close();
      process.exit(0);
    }),
  );
