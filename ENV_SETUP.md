# SurakshaSetu — environment setup

**Render free / Brevo API:** use [the Brevo setup guide](BREVO_SETUP.md). SMTP port restrictions below do not apply to Brevo HTTPS delivery. Set `EMAIL_PROVIDER=brevo`, `BREVO_API_KEY`, and `BREVO_SENDER_EMAIL`.

Android now defaults to `https://skill-kavach.onrender.com/`. Build with `.\gradlew.bat :app:assembleDebug` and install the new APK to use the hosted backend over Wi-Fi or mobile data. No local server is required. Remove any old `API_BASE_URL` override from your user Gradle properties if it points to localhost or the emulator.

**Hosting on Render:** follow [the Render deployment guide](deploy/RENDER.md) for the reviewed environment, Blueprint setup, database TLS configuration, secret files and Android URL update.

Start with **Option A** to try the whole local workflow without paying for email, creating a Firebase project, or installing PostgreSQL. Use **Option B** for a persistent PostgreSQL setup. Production configuration is explained afterward.

The app is always light, even when the phone uses dark mode. Install the latest rebuilt APK after changing code; changing `.env` alone does not update an installed Android APK.

## 1. Understand the three configuration locations

| Location | Used by | Contents |
|---|---|---|
| `backend/.env` | Node API launched with `npm start`, `npm run migrate`, or `npm run seed` from `backend` | Database URL, server secrets, email configuration, optional Firebase server configuration |
| Repository-root `.env` | Local `docker compose` | Local PostgreSQL password |
| `%USERPROFILE%/.gradle/gradle.properties` or Gradle `-P` arguments | Android build | API base URL and optional Firebase **client** configuration |
| `deploy/.env.production` | Production Docker Compose, when passed with `--env-file` | Domain, database/server secrets and service configuration |

The React console uses the same origin as the API. It does **not** need a separate frontend `.env`. In development, Vite proxies `/api` to `http://127.0.0.1:8080`.

`local.properties` at the repository root contains your Android SDK path. It is **not** the API environment file. Leave `sdk.dir` as configured by Android Studio.

Do not place `JWT_SECRET`, `OTP_PEPPER`, SMTP secrets, database passwords, or Firebase service-account JSON in the Android app or React code. The client Firebase API key is a different kind of value from a server service-account private key.

## 2. Option A — run locally without external services

Requires Node.js 22 or newer. In PowerShell:

```powershell
Set-Location 'D:\Skill Kavach\admin'
npm.cmd ci
npm.cmd run build

Set-Location 'D:\Skill Kavach\backend'
npm.cmd ci
node scripts/dev-local.js
```

Keep that terminal running. Open [the local console](http://localhost:8080).

Use these **local sandbox accounts only**:

| Login field | Value |
|---|---|
| Organization ID | `local` |
| Administrator employee ID | `ADMIN` |
| Worker employee ID | `WORKER` |
| Trainer employee ID | `TRAINER` |

After clicking **Send verification code**, read the newly generated code in another terminal:

```powershell
Get-Content 'D:\Skill Kavach\backend\.data\otp.json'
```

Enter the `code` value. It expires after five minutes and can be used once. Wait one minute before requesting another code. The file is for local development; real email is not sent. The most recent request replaces the local code file.

The sandbox stores data in `backend/.data/local-postgres`. Its signing secrets change on restart, so sign in again after restarting it. This runner does not load `backend/.env`; it is deliberately independent of production credentials and refuses `NODE_ENV=production`.

### Connect the Android emulator

```powershell
Set-Location 'D:\Skill Kavach'
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8080/
```

Install `app/build/outputs/apk/debug/app-debug.apk` on the emulator, or set the same property in your user Gradle file and run the app from Android Studio.

`10.0.2.2` is the Android emulator's route to this computer. `localhost` inside a phone means **the phone**, not your computer. The local debug build permits HTTP only for `10.0.2.2`; release builds require HTTPS. A physical phone needs a reachable HTTPS API URL, with a certificate trusted by Android. Do not bypass TLS certificate checks.

For emulator-generated certificate links, run the regular API with `PUBLIC_URL=http://10.0.2.2:8080`; the sandbox's default `localhost` verification links are intended for desktop browser testing.

## 3. Option B — local PostgreSQL with generated `.env` files

Requires Docker Desktop running, or an independently installed PostgreSQL server.

This helper creates independent random server secrets and a matching database password. It refuses to overwrite existing environment files.

```powershell
Set-Location 'D:\Skill Kavach\backend'
node scripts/setup-env.js
```

It creates:

- `D:\Skill Kavach\backend\.env`
- `D:\Skill Kavach\.env`

Open `backend/.env` and set:

```dotenv
BOOTSTRAP_ADMIN_EMAIL=your-real-address@example.com
```

Replace the example with your real administrator email and configure SMTP using section 4. Normal server startup requires SMTP credentials.

Then:

```powershell
Set-Location 'D:\Skill Kavach'
docker compose up -d db

Set-Location 'D:\Skill Kavach\backend'
npm.cmd ci
npm.cmd run migrate
npm.cmd run seed
npm.cmd start
```

Build the React console as shown in Option A before opening [the console](http://localhost:8080). With the generated configuration, organization ID is `demo` and administrator employee ID is `ADMIN`. These differ from the independent sandbox's `local` organization.

Sign in as the administrator, then use **Workers → Add employee** to create real workers and trainers. A worker's employee ID is the value you enter in that form; it is not automatically their phone number.

If Docker is unavailable, install PostgreSQL separately, create a database and login, and replace `DATABASE_URL` with that connection string. Skip the `docker compose` command.

## 4. Backend environment and SMTP email OTP

Use [backend/.env.example](backend/.env.example). Normal startup sends all OTPs through authenticated SMTP; the standalone `scripts/dev-local.js` sandbox is the only file-based test delivery harness.

| Variable | Source / value |
|---|---|
| `DATABASE_URL` | PostgreSQL provider connection string |
| `DATABASE_CA_CERT` | Provider CA PEM path if required |
| `JWT_SECRET`, `OTP_PEPPER` | Independent cryptographically random secrets, at least 32 characters |
| `NODE_ENV` | `production` on Render |
| `PUBLIC_URL` | HTTPS origin; omit on Render to use its assigned URL |
| `PROXY_HOPS` | `1` behind Render ingress; `0` for direct local use |
| `PORT` | Let Render supply it, or `8080` locally |
| `SMTP_HOST` / `EMAIL_HOST` | Your provider's SMTP hostname |
| `SMTP_PORT` / `EMAIL_PORT` | Provider port, commonly `587` (STARTTLS) or `465` (implicit TLS) |
| `SMTP_SECURE` / `EMAIL_SECURE` | Optional `true` for implicit TLS; defaults to true only on port 465. STARTTLS remains required on other ports |
| `SMTP_USER` / `EMAIL_USER` | SMTP authentication username |
| `SMTP_PASS` / `EMAIL_PASS` | SMTP password/app password supplied by the provider |
| `SMTP_FROM` / `EMAIL_FROM` | Authenticated mailbox or provider-approved sender; defaults to SMTP user |
| `BOOTSTRAP_ORG` | Organization ID, e.g. `demo` |
| `BOOTSTRAP_ADMIN_EMAIL` | Real login email for `ADMIN` |
| `BOOTSTRAP_SECOND_ADMIN_EMAIL` | Optional real login email for separate `ADMIN2` account |

Choose either SMTP_* or EMAIL_* names consistently; SMTP_* takes precedence. Existing EMAIL_* values work. Do not place SMTP credentials in Android or frontend code. Remove obsolete SMS delivery and provider-selection variables from Render; keep your SMTP/EMAIL credentials.

## 5. Verify email configuration and migrate accounts

From `backend`, run `npm run check:smtp`. It verifies connection, TLS and authentication without sending email. Successful SMTP acceptance does not guarantee inbox delivery; test actual login and check spam/provider logs afterward.

Run `npm run migrate` to add account/challenge email fields and invalidate former sessions once. Run `npm run seed` with explicit bootstrap emails to migrate existing administrators or create new ones. Seed does not overwrite an already assigned email. Existing workers need an organization administrator to register their email in the console.

**Render free services block SMTP ports 25, 465 and 587.** Your configured port 587 needs a Render plan or host permitting SMTP. Do not disable TLS to work around this. [Render's documented restriction](https://render.com/docs/free).

## 6. Optional Firebase push notifications

The inbox and manual/background synchronization work without Firebase. Configure Firebase only when you want device push notifications.

### Firebase server values

1. Create or select your Firebase project.
2. In project settings, copy its **Project ID** to backend `FIREBASE_PROJECT_ID`.
3. Enable Cloud Messaging for the project.
4. Configure Application Default Credentials. If using a downloaded service-account key, keep it outside the repository and set `GOOGLE_APPLICATION_CREDENTIALS` to its absolute path. On Windows, a path such as `C:/Users/YourName/.secrets/suraksha-service-account.json` works.
5. Give the service identity the FCM permissions required by your project. Follow the [Firebase Admin setup guide](https://firebase.google.com/docs/admin/setup) and [FCM send guide](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk).

Never copy that service-account JSON into `app/src`, React code, or a public bucket.

### Firebase Android values

Register an **Android app** in the same Firebase project using package name:

```text
com.example.skilkavach
```

Download that app's `google-services.json` from Firebase. This project initializes Firebase programmatically, so you copy the following four values into Gradle properties; you do not need to add the Google Services Gradle plugin.

| Gradle property | Value in the matching Android client's `google-services.json` |
|---|---|
| `FIREBASE_APP_ID` | `client[].client_info.mobilesdk_app_id` |
| `FIREBASE_API_KEY` | `client[].api_key[].current_key` |
| `FIREBASE_PROJECT_ID` | `project_info.project_id` |
| `FIREBASE_SENDER_ID` | `project_info.project_number` |

Choose the `client` entry whose `android_client_info.package_name` equals `com.example.skilkavach`. These values belong to your Firebase Android app; do not substitute the backend service-account private key.

Example user Gradle file at `%USERPROFILE%/.gradle/gradle.properties`:

```properties
API_BASE_URL=https://safety.yourdomain.com/
FIREBASE_APP_ID=YOUR_MOBILESDK_APP_ID
FIREBASE_API_KEY=YOUR_ANDROID_CLIENT_API_KEY
FIREBASE_PROJECT_ID=YOUR_PROJECT_ID
FIREBASE_SENDER_ID=YOUR_PROJECT_NUMBER
```

Replace the values and rebuild/reinstall the app. Then open **Profile → Enable push notifications** and grant the Android permission. Firebase delivery is optional and does not replace the site's emergency response process. The inbox records remain available even if a push fails. The [Android FCM setup documentation](https://firebase.google.com/docs/cloud-messaging/android/get-started) explains project/client registration and notification permissions.

## 7. Every Android variable

| Variable | Local emulator | Physical phone / production |
|---|---|---|
| `API_BASE_URL` | `http://10.0.2.2:8080/` | Your reachable HTTPS API origin **ending with `/`**, without `/api` |
| `FIREBASE_APP_ID` | Empty if push is not configured | Firebase Android `mobilesdk_app_id` |
| `FIREBASE_API_KEY` | Empty if push is not configured | Firebase Android client API key |
| `FIREBASE_PROJECT_ID` | Empty if push is not configured | Same project ID as the server |
| `FIREBASE_SENDER_ID` | Empty if push is not configured | Firebase project number |

An Android rebuild is required after changing these properties. Restarting Node alone does not change the app's API URL.

To build a release after configuring a real API:

```powershell
.\gradlew.bat :app:assembleRelease -PAPI_BASE_URL=https://safety.yourdomain.com/
```

Release signing is intentionally not configured with a shared or generated-in-source secret. Use Android Studio's **Generate Signed Bundle / APK** flow and your organization's signing key. Keep that key and its passwords outside Git.

## 8. Production Docker environment

Copy `deploy/production.env.example` to `deploy/.env.production`, then fill it in. This file is ignored by Git.

| Additional variable | Source |
|---|---|
| `APP_DOMAIN` | A domain/subdomain you control, e.g. `safety.yourdomain.com`; point its DNS at the deployment host |
| `POSTGRES_PASSWORD` | Generate a fresh database password; it must match the password in `DATABASE_URL` |
| `DATABASE_URL` | For the supplied private database: `postgresql://suraksha:YOUR_PASSWORD@db:5432/suraksha` |
| `FIREBASE_CREDENTIALS_DIR` | Optional host directory containing a file named `service-account.json`; mounted read-only at `/run/firebase` |

All other variables are described above. Production Compose sets `PUBLIC_URL=https://APP_DOMAIN`, `NODE_ENV=production`, and `PROXY_HOPS=1` for you. If Firebase is unused, leave `FIREBASE_PROJECT_ID` empty and the credentials directory can remain empty.

From the repository root on your deployment host:

```powershell
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml up -d db
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml build api
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml run --rm api node src/migrate.js
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml run --rm -e BOOTSTRAP_ORG=YOUR_ORG -e BOOTSTRAP_ADMIN_EMAIL=admin@example.com api node src/seed.js
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml up -d
```

Replace `YOUR_ORG` and `YOUR_E164_NUMBER` before running the seed command. The gateway handles HTTPS for the configured domain. Only the gateway exposes public ports; the API/database remain private. Arrange database backups, restoration checks, secret storage, and operational monitoring for your deployment.

## 9. Troubleshooting

- A registered email is required for every account; typing a new address at login does not change it.
- Deploy backend/schema and provision administrator emails before installing the new APK. Older phone-login clients are not compatible.
- SMTP timeout on Render: check the SMTP port restriction above and provider/network access.
- SMTP authentication failure: check SMTP username and provider app-password requirements.
- No email despite a generic response: confirm organization, employee ID, registered email and approval status; check spam/provider delivery logs.
- Existing administrator email differs from seed configuration: use the authorized console update flow. Seed deliberately refuses to overwrite identities.
- Firebase remains optional and independent of email login.
