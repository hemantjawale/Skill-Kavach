// Local validation environment only. PGlite executes real PostgreSQL, but is not a production server.
import { PGlite } from "@electric-sql/pglite";
import { randomBytes } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { schema } from "../src/migrate.js";
import { createApp } from "../src/app.js";
if (process.env.NODE_ENV === "production")
  throw new Error("Local development runner is disabled in production.");
await mkdir(".data", { recursive: true });
const pg = new PGlite(".data/local-postgres");
await pg.exec(schema);
const db = {
  query: (q, p) => pg.query(q, p),
  transaction: (fn) => pg.transaction(fn),
};
await db.query(
  "INSERT INTO organizations VALUES('local','Local training sandbox') ON CONFLICT DO NOTHING",
);
for (const [id, name, role, phone] of [
  ["ADMIN", "Local administrator", "ORG_ADMIN", "+919000000001"],
  ["WORKER", "Practice worker", "WORKER", "+919000000002"],
  ["TRAINER", "Local trainer", "TRAINER", "+919000000003"],
]) {
  await db.query(
    "INSERT INTO users(id,org_id,employee_id,name,phone,role,site) VALUES($1,$2,$1,$3,$4,$5,$6) ON CONFLICT DO NOTHING",
    [id, "local", name, phone, role, "Bokaro"],
  );
}
const app = createApp(db, {
  jwtSecret: randomBytes(48).toString("hex"),
  otpPepper: randomBytes(48).toString("hex"),
  publicUrl: "http://localhost:8080",
  adminPath: resolve("../admin/dist"),
  async sendOtp(phone, code, challengeId) {
    await writeFile(".data/otp.json", JSON.stringify({ challengeId, code }), {
      mode: 0o600,
    });
  },
});
const server = app.listen(8080, "127.0.0.1", () =>
  console.log(
    "LOCAL SANDBOX: http://localhost:8080; organization local; employees ADMIN / WORKER / TRAINER. OTP: backend/.data/otp.json",
  ),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () =>
    server.close(async () => {
      await pg.close();
      process.exit(0);
    }),
  );
