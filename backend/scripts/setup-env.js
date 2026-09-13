import { randomBytes } from "node:crypto";
import { access, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const apiPath = fileURLToPath(new URL("../.env", import.meta.url)),
  dockerPath = fileURLToPath(new URL("../../.env", import.meta.url));
for (const path of [apiPath, dockerPath]) {
  if (
    await access(path).then(
      () => true,
      () => false,
    )
  )
    throw new Error(
      `${path} already exists. It was not changed. Edit it using ENV_SETUP.md.`,
    );
}
const password = randomBytes(32).toString("hex");
await writeFile(
  apiPath,
  `NODE_ENV=development\nPORT=8080\nDATABASE_URL=postgresql://suraksha:${password}@127.0.0.1:5432/suraksha\nJWT_SECRET=${randomBytes(48).toString("hex")}\nOTP_PEPPER=${randomBytes(48).toString("hex")}\nPUBLIC_URL=http://localhost:8080\nSMS_PROVIDER=local\nDEV_OTP_FILE=.data/otp.json\nPROXY_HOPS=0\nFIREBASE_PROJECT_ID=\nGOOGLE_APPLICATION_CREDENTIALS=\nBOOTSTRAP_ORG=demo\nBOOTSTRAP_ADMIN_PHONE=\n`,
  { flag: "wx", mode: 0o600 },
);
await writeFile(dockerPath, `POSTGRES_PASSWORD=${password}\n`, {
  flag: "wx",
  mode: 0o600,
});
console.log(
  "Created backend/.env and root .env with independent random secrets. Set BOOTSTRAP_ADMIN_PHONE, then follow ENV_SETUP.md. No existing files were overwritten.",
);
