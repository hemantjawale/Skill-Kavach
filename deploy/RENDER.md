# Deploy SurakshaSetu on Render

The repository now includes `render.yaml`. It builds the Android API and React staff console into one Docker web service, using your existing PostgreSQL database. It does not deploy the Android app or provision a second database. The selected compute plan is paid; review Render's price before creating the service. One instance is configured because HTTP rate limits currently use process memory.

## Environment review — 14 September 2026

Your local `backend/.env` was inspected without printing secret values. After you supplied the CA certificate, only `DATABASE_CA_CERT` was added, pointing to `D:/EDGE DOWNLOADS/ca.pem`.

| Item | Result |
|---|---|
| JWT_SECRET / OTP_PEPPER | Both 128 characters and different; structurally acceptable. Entropy cannot be proven from length. |
| Twilio account SID, auth token, sender and Messaging Service SID | Expected formats; actual provider permissions and delivery still need verification. |
| Firebase credentials | File exists, service-account private key parses, project ID matches. Live push delivery is not tested. |
| DATABASE_URL | Authenticated connection now passes with the supplied CA and certificate verification enabled. All ten application tables are absent; the Render pre-deploy migration will create them. |
| NODE_ENV | Currently `development`; use `production` on Render. |
| SMS_PROVIDER | Currently `local`; use `twilio` on Render. Existing Twilio values are ignored in local mode. |
| PUBLIC_URL | Currently localhost; omit it on Render to use `RENDER_EXTERNAL_URL` automatically, or set the actual HTTPS custom origin. |
| PROXY_HOPS | Currently `0`; Blueprint sets `1` for Render's ingress. Reassess before adding another reverse proxy. |
| BOOTSTRAP_ADMIN_PHONE | Empty; must be your actual SMS-receiving administrator number in E.164 format before seeding. |
| GOOGLE_APPLICATION_CREDENTIALS | Local path works only on this computer; replace with Render's secret-file path. |

## 1. Configure the database CA

Your supplied `D:/EDGE DOWNLOADS/ca.pem` is a currently valid CA certificate, and the database accepts an authenticated connection with it. The local backend is configured to use it. Upload the same PEM to Render as a Secret File named `database-ca.pem`, and set:

```dotenv
DATABASE_CA_CERT=/etc/secrets/database-ca.pem
```

The backend, migration and seed commands now use that CA with certificate and hostname verification enabled. This overrides URL TLS options so `pg` cannot discard the CA. Keep the provider's full DNS hostname in `DATABASE_URL`. Do not use `NODE_TLS_REJECT_UNAUTHORIZED=0` or disable certificate verification.

For local testing, set `DATABASE_CA_CERT` in `backend/.env` to the downloaded PEM's absolute Windows path. If your provider uses a publicly trusted chain, use its current recommended connection URL and `sslmode=verify-full`; leave `DATABASE_CA_CERT` unset. A missing intermediate certificate may require a provider-supplied CA bundle. The observed error does not prove that the password is incorrect.

Alternatively, if you choose Render Postgres later, replace `DATABASE_URL` with its internal connection string and keep database and web service in the same region. The current Blueprint intentionally uses your existing database.

## 2. Create the service

1. Push the code and `render.yaml` to your Git repository. Do not commit `.env`, private keys, or service-account files.
2. In Render choose **New → Blueprint**, connect that repository and select its deployment branch.
3. Review the single Docker web service and paid compute plan. Region is Singapore; change it before creation if your database is closer to another Render region.
4. Fill the prompted variables using the values below. Add any required secret files and optional Firebase variables before the successful deployment. If the first migration fails before secret files are configured, add them and manually redeploy.
5. Render runs `node src/migrate.js` before deploying, then starts the API. Migrations create the schema; they do not create the first administrator.

For manual **New → Web Service** instead of Blueprint: select Docker, leave Root Directory empty, set Dockerfile Path to `./Dockerfile`, Docker Context to `.`, Pre-Deploy Command to `node src/migrate.js`, and Health Check Path to `/health`. Leave Docker Command empty. Use a paid service with pre-deploy support. The root directory must include both `backend` and `admin`.

## 3. Render environment values

| Variable | Set on Render |
|---|---|
| `NODE_ENV` | `production` (Blueprint supplies it) |
| `PORT` | Let Render supply it; server and Docker health check follow it automatically |
| `DATABASE_URL` | Your real PostgreSQL connection URL, including the database password; resolve TLS first |
| `DATABASE_CA_CERT` | `/etc/secrets/database-ca.pem` if your provider requires its CA |
| `JWT_SECRET`, `OTP_PEPPER` | Copy your existing independent secrets privately into Render. Keep them stable across deploys |
| `PROXY_HOPS` | `1` (Blueprint supplies it) |
| `PUBLIC_URL` | Omit entirely for the Render URL; for a custom domain set `https://your-domain` with no path/query |
| `SMS_PROVIDER` | `twilio` (Blueprint supplies it) |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | Your existing values |
| `TWILIO_MESSAGING_SERVICE_SID` | Your existing `MG...` value; its service must have a usable SMS sender |
| `TWILIO_FROM` | Optional when Messaging Service SID is set; that service takes precedence. For number-only sending, omit the Messaging Service SID and set your Twilio SMS number |
| `BOOTSTRAP_ORG` | Your chosen organization ID; use the same ID at login |
| `BOOTSTRAP_ADMIN_PHONE` | Your actual administrator phone, including country code, e.g. `+91` followed by the number |
| `FIREBASE_PROJECT_ID` | Optional: existing matching project ID, after adding the service-account secret file |
| `GOOGLE_APPLICATION_CREDENTIALS` | Optional: `/etc/secrets/firebase-service-account.json` |

Do not copy the local `.env` wholesale: it would override production mode, proxy trust, SMS selection and the URL with development values. `DEV_OTP_FILE` and the unused webhook variables are unnecessary on Render. Backend secrets never belong in Android Gradle properties.

For Firebase, open **Environment → Secret Files**, name the file `firebase-service-account.json`, and paste the contents of your existing service-account JSON. Set both Firebase variables above and deploy. To defer push setup, leave both unset; the in-app inbox remains available. A Windows file path will not work in Render's Linux container.

## 4. Create the first administrator

After deployment is healthy, open the Render service's **Shell** and run:

```sh
node src/seed.js
```

This uses `BOOTSTRAP_ORG` and `BOOTSTRAP_ADMIN_PHONE`. Log in at the service's HTTPS URL using that organization ID and employee ID `ADMIN`. It should send a real OTP through Twilio. The seed is idempotent, but it does not change the phone of an existing administrator; editing the environment is not an account-phone update.

Twilio delivery also depends on your account permissions, SMS sender configuration, destination permissions and any applicable country registration. Verify in Twilio's delivery logs if the OTP is not received. No test SMS was sent during this configuration review.

## 5. Point Android to Render

Use the exact HTTPS URL shown by Render, with a trailing slash and without `/api`:

```powershell
Set-Location 'D:\Skill Kavach'
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=https://YOUR-SERVICE.onrender.com/
```

Install the newly built `app/build/outputs/apk/debug/app-debug.apk`. The previous APK still targets the emulator backend until rebuilt. Keep your four Android Firebase client properties from `ENV_SETUP.md` if you want push. The React staff console is already served at the same HTTPS origin; it needs no CORS or separate frontend environment setup.

## 6. Verify and update

- Visit `https://YOUR-SERVICE.onrender.com/health`: expect `{"status":"ok"}`. This checks database connectivity.
- Open the same host's root URL: expect the staff login screen.
- Sign in with a real OTP, create a worker, and verify Android login and sync over mobile data.
- Verify push on a physical phone after enabling it. HTTPS alone does not configure Firebase.
- Auto-deploy is off in the Blueprint. After pushing updates, choose **Manual Deploy → Deploy latest commit**. The pre-deploy migration runs again before the new version starts.

Deployment was prepared locally; no Render service was created and no changes were made to your remote database during review.

Official references: [Render Blueprint specification](https://render.com/docs/blueprint-spec), [environment variables and secret files](https://render.com/docs/configure-environment-variables), [web-service port binding](https://render.com/docs/web-services), [PostgreSQL connections](https://render.com/docs/postgresql-creating-connecting).
