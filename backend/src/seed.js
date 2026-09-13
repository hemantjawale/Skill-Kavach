import { database } from "./db.js";
const db = database(process.env.DATABASE_URL);
const phone = process.env.BOOTSTRAP_ADMIN_PHONE;
if (!/^\+[1-9]\d{7,14}$/.test(phone ?? ""))
  throw new Error(
    "Set BOOTSTRAP_ADMIN_PHONE to the actual administrator phone (E.164).",
  );
try {
  await db.transaction(async (tx) => {
    await tx.query(
      "INSERT INTO organizations(id,name) VALUES($1,$2) ON CONFLICT DO NOTHING",
      [process.env.BOOTSTRAP_ORG ?? "demo", "SurakshaSetu"],
    );
    await tx.query(
      "INSERT INTO users(id,org_id,employee_id,name,phone,role,site) VALUES($1,$2,$3,$4,$5,$6,$7) ON CONFLICT DO NOTHING",
      [
        "bootstrap-admin",
        process.env.BOOTSTRAP_ORG ?? "demo",
        "ADMIN",
        "Organization administrator",
        phone,
        "ORG_ADMIN",
        "Bokaro",
      ],
    );
  });
  console.log("Administrator provisioned. Use OTP login.");
} finally {
  await db.close();
}
