# SMTP email OTP deployment — Render Node.js

**Render free / Brevo API:** use [the Brevo setup guide](../BREVO_SETUP.md). SMTP port restrictions below do not apply to Brevo HTTPS delivery. Set `EMAIL_PROVIDER=brevo`, `BREVO_API_KEY`, and `BREVO_SENDER_EMAIL`.

The backend and both login clients now use email OTP only. The former SMS transport, webhook fallback, fallback email recipient and debug-code responses have been removed. Existing EMAIL_* environment values are supported; SMTP_* takes precedence.

## 1. Hosting requirement

Render free web services block outbound ports **25, 465 and 587**. Your existing SMTP configuration uses **587**. Use a paid Render instance or an SMTP-capable host before expecting email delivery. The app cannot bypass a hosting firewall. See [Render's free-service restrictions](https://render.com/docs/free).

On this workstation, `npm run check:smtp` passed connection, TLS and authentication on 23 September 2026. No live email was sent; inbox delivery and the Render connection still need verification after deployment.

## 2. Environment

Keep your current database URL, database CA secret file, JWT/OTP secrets, proxy settings and optional Firebase settings. Remove obsolete `SMS_*`, `TWILIO_*`, `OTP_PROVIDER` and `DEV_OTP_FILE` variables from Render.

Copy your configured values privately into Render:

```dotenv
NODE_ENV=production
PROXY_HOPS=1
EMAIL_HOST=your-provider-host
EMAIL_PORT=587
EMAIL_USER=your-smtp-user
EMAIL_PASS=your-smtp-password-or-app-password
EMAIL_FROM=your-provider-approved-sender
BOOTSTRAP_ORG=demo
BOOTSTRAP_ADMIN_EMAIL=first-administrator@example.com
BOOTSTRAP_SECOND_ADMIN_EMAIL=second-administrator@example.com
```

Use the two administrator addresses you supplied: the first is assigned to employee ID `ADMIN`, the second to `ADMIN2`. They are saved in your private local `backend/.env`; copy them to Render. These are separate identities with separate OTP recipients. SMTP credentials do not automatically assign login ownership. `BOOTSTRAP_SECOND_ADMIN_EMAIL` is optional for installations with one administrator.

Do not change the organization ID if the existing organization is different from `demo`. Leave `PUBLIC_URL` unset to use Render's assigned HTTPS URL. `DATABASE_CA_CERT` remains the absolute Render secret-file path, not the Windows path. Keep `SMTP_SECURE`/`EMAIL_SECURE` unset for automatic port-based selection, or false for STARTTLS on 587. TLS verification stays enabled.

## 3. Build and migration (your existing root is backend)

Push all changes to the connected deployment branch. In Render:

- **Root Directory:** `backend`
- **Build Command:** `npm --prefix ../admin install --include=dev && npm --prefix ../admin run build && npm install --omit=dev`
- **Start Command for the first email migration:** `npm run migrate && npm run seed && npm start`
- **Health Check Path:** `/health`

For a repository-root service instead, leave Root Directory empty, use `npm --prefix admin install --include=dev && npm --prefix admin run build && npm --prefix backend install --omit=dev`, and start with `cd backend && npm run migrate && npm run seed && npm start`.

Deploy backend/schema first, then install the new APK. The migration is additive: existing employee IDs, phone contacts and workforce records stay intact. It adds nullable email for legacy accounts and invalidates all pre-migration sessions/codes **once**. Seed assigns the first administrator's email only if absent, and creates/migrates the second administrator separately. It refuses to overwrite an already configured address or elevate a non-admin account. Do not delete/recreate your database.

After the first successful deployment, restore **Start Command** to `npm run migrate && npm start` for the backend-root configuration. On a paid instance you can instead run migration/seed as controlled pre-deploy tasks, then use `npm start`.

## 4. Existing and new employees

1. Open https://skill-kavach.onrender.com/manager.
2. Sign in with the saved organization ID, `ADMIN` and its configured email (or `ADMIN2` and the second email).
3. In **Workers → Set login email**, register each existing worker's actual email. This invalidates that worker's sessions and unconsumed codes. Email updates are organization-admin only and audited.
4. Create new workers with an email in **Add employee**, or review their mobile self-registration under **Pending Approvals**. Approval is still required before a self-registered worker can sign in.
5. Install the new Android APK and sign in using organization, employee ID and registered email. The former phone-based APK is incompatible.

If seed reports an administrator email already exists, do not force overwrite it. Sign in with the existing identity and change it through an authorized administrator, or inspect the database using your trusted operational recovery process.

## 5. Verify

From Render Shell, run `npm run check:smtp` to test SMTP TLS/authentication without sending a message. Verify `/health`, then request a real login code yourself. Check inbox/spam and provider logs. Successful SMTP authentication alone does not verify permission to use a particular From address or guarantee inbox delivery.

Codes expire after five minutes, are one-use and allow at most five incorrect attempts. Requests have IP and account throttles. A wrong email returns a generic response but cannot receive another employee's code. No OTP is returned as `debugCode`, logged, or sent to the SMTP sender as a fallback.

Local unit/integration tests use injected mail transports and a disposable PostgreSQL-compatible database; they do not send messages or modify your hosted accounts. Hosted migration and inbox testing remain deployment steps.
