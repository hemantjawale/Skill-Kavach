import { randomUUID } from "node:crypto";
import { database } from "./db.js";
import { emailAddress } from "./email.js";
const org = process.env.BOOTSTRAP_ORG || "demo";
const entries = [
  ["ADMIN", emailAddress.parse(process.env.BOOTSTRAP_ADMIN_EMAIL)],
];
if (process.env.BOOTSTRAP_SECOND_ADMIN_EMAIL)
  entries.push([
    "ADMIN2",
    emailAddress.parse(process.env.BOOTSTRAP_SECOND_ADMIN_EMAIL),
  ]);
const db = database(process.env.DATABASE_URL);
try {
  await db.transaction(async (tx) => {
    await tx.query(
      "INSERT INTO organizations(id,name) VALUES($1,$2) ON CONFLICT DO NOTHING",
      [org, "SurakshaSetu"],
    );
    for (const [employee, email] of entries) {
      const existing = (
        await tx.query(
          "SELECT * FROM users WHERE org_id=$1 AND employee_id=$2 FOR UPDATE",
          [org, employee],
        )
      ).rows[0];
      if (existing) {
        if (existing.role !== "ORG_ADMIN" || !existing.active)
          throw new Error(
            "Bootstrap employee ID belongs to a non-administrator or inactive account.",
          );
        if (existing.email && existing.email !== email)
          throw new Error(
            "Administrator email already set. Use the authorized email update flow instead of overwriting it through seed.",
          );
        if (!existing.email) {
          await tx.query("UPDATE users SET email=$1 WHERE id=$2", [
            email,
            existing.id,
          ]);
          await tx.query("UPDATE sessions SET revoked=true WHERE user_id=$1", [
            existing.id,
          ]);
          await tx.query(
            "UPDATE otp_challenges SET consumed=true WHERE user_id=$1",
            [existing.id],
          );
          await tx.query(
            "INSERT INTO audit_log(id,org_id,actor_id,action,record_id) VALUES($1,$2,$3,$4,$3)",
            [randomUUID(), org, existing.id, "bootstrap.email.migrate"],
          );
        }
      } else {
        await tx.query(
          "INSERT INTO users(id,org_id,employee_id,name,email,role,site) VALUES($1,$2,$3,$4,$5,'ORG_ADMIN','Bokaro')",
          [randomUUID(), org, employee, "Organization administrator", email],
        );
      }
    }
  });
  console.log(
    "Administrator email accounts provisioned. Sign in with email OTP.",
  );
} finally {
  await db.close();
}
