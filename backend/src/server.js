import { resolve } from "node:path";
import { database } from "./db.js";
import { createApp } from "./app.js";
import { startPushWorker } from "./push.js";
import { createOtpSender } from "./sms.js";
const production = process.env.NODE_ENV === "production";
const publicUrl = process.env.PUBLIC_URL;
if (production && !publicUrl?.startsWith("https://"))
  throw new Error("Production requires an HTTPS public URL.");
const db = database(process.env.DATABASE_URL);
const app = createApp(db, {
  proxyHops: process.env.PROXY_HOPS === "1" ? 1 : 0,
  jwtSecret: process.env.JWT_SECRET,
  otpPepper: process.env.OTP_PEPPER,
  publicUrl: publicUrl ?? "http://localhost:8080",
  adminPath: resolve("../admin/dist"),
  sendOtp: createOtpSender(process.env),
});
const stopPush = startPushWorker(db, process.env.FIREBASE_PROJECT_ID);
const server = app.listen(Number(process.env.PORT ?? 8080), "0.0.0.0", () =>
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
