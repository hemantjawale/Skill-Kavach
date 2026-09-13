# SurakshaSetu — environment setup

Start with **Option A** to try the whole local workflow without paying for SMS, creating a Firebase project, or installing PostgreSQL. Use **Option B** for a persistent PostgreSQL setup. Production configuration is explained afterward.

The app is always light, even when the phone uses dark mode. Install the latest rebuilt APK after changing code; changing `.env` alone does not update an installed Android APK.

## 1. Understand the three configuration locations

| Location | Used by | Contents |
|---|---|---|
| `backend/.env` | Node API launched with `npm start`, `npm run migrate`, or `npm run seed` from `backend` | Database URL, server secrets, SMS configuration, optional Firebase server configuration |
| Repository-root `.env` | Local `docker compose` | Local PostgreSQL password |
| `%USERPROFILE%/.gradle/gradle.properties` or Gradle `-P` arguments | Android build | API base URL and optional Firebase **client** configuration |
| `deploy/.env.production` | Production Docker Compose, when passed with `--env-file` | Domain, database/server secrets and service configuration |

The React console uses the same origin as the API. It does **not** need a separate frontend `.env`. In development, Vite proxies `/api` to `http://127.0.0.1:8080`.

`local.properties` at the repository root contains your Android SDK path. It is **not** the API environment file. Leave `sdk.dir` as configured by Android Studio.

Do not place `JWT_SECRET`, `OTP_PEPPER`, Twilio secrets, database passwords, or Firebase service-account JSON in the Android app or React code. The client Firebase API key is a different kind of value from a server service-account private key.

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

Enter the `code` value. It expires after five minutes and can be used once. Wait one minute before requesting another code. The file is for local development; real SMS is not sent. The most recent request replaces the local code file.

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
BOOTSTRAP_ADMIN_PHONE=+91YOUR_REAL_10_DIGIT_NUMBER
```

Replace the entire example with your real number, e.g. `+91` followed by your ten digits, with no spaces. This is the account's registered phone. With `SMS_PROVIDER=local`, codes still go to the local file; switching to a real provider later uses this registered phone.

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

## 4. Every backend variable and where its value comes from

| Variable | What to enter / where to obtain it | Needed? |
|---|---|---|
| `NODE_ENV` | `development` locally; `production` on the deployed server | Yes |
| `PORT` | Port on which Node listens; use `8080` unless your host supplies another value | Default `8080` |
| `DATABASE_URL` | PostgreSQL connection string. Generated by the local helper. For hosted PostgreSQL, copy the provider's connection string and use its required TLS/CA settings. | Yes for the regular API |
| `JWT_SECRET` | Generate a fresh random secret using the command below. Used to sign access tokens. It does not come from Firebase. | Yes |
| `OTP_PEPPER` | Generate a **different** random secret with the same command. Used to hash OTP values. | Yes |
| `PUBLIC_URL` | Browser-accessible API/admin origin, e.g. `http://localhost:8080` locally or `https://safety.yourdomain.com` in production. Do not append `/api`. Used in certificate verification links. | HTTPS required in production |
| `SMS_PROVIDER` | `local`, `twilio`, or `webhook`. `local` writes a development code file and is rejected in production. | Yes; defaults to local when no webhook is configured |
| `DEV_OTP_FILE` | Local file path for development OTPs; `.data/otp.json` is sufficient. Relative to `backend` when using the documented commands. | Local delivery only |
| `TWILIO_ACCOUNT_SID` | Account SID from your Twilio console; starts with `AC` | Twilio only |
| `TWILIO_AUTH_TOKEN` | Auth token from the same Twilio account's console. Keep private. | Twilio only |
| `TWILIO_FROM` | SMS-capable sender number or sender configured in that Twilio account | Twilio, unless using a Messaging Service SID |
| `TWILIO_MESSAGING_SERVICE_SID` | SID of your configured Twilio Messaging Service; starts with `MG`. If supplied, the service selects the sender and `TWILIO_FROM` is not used. | Optional alternative to `TWILIO_FROM` |
| `SMS_WEBHOOK_URL` | HTTPS endpoint of an SMS bridge you operate, implementing the contract below. This is **not** an arbitrary provider console URL. | Webhook only |
| `SMS_WEBHOOK_TOKEN` | Bearer secret accepted by that bridge. Generate it and configure the same value in the bridge. | Webhook only |
| `FIREBASE_PROJECT_ID` | Firebase console → project settings → Project ID. For server push delivery. | Optional; empty disables the push worker |
| `GOOGLE_APPLICATION_CREDENTIALS` | Absolute path to a protected Firebase/Google service-account JSON file, or use Google Application Default Credentials / workload identity supported by your host. This variable contains a **path**, not the JSON contents. | When Firebase push uses a service-account file |
| `PROXY_HOPS` | `0` locally. `1` only when requests must pass through the supplied single trusted gateway and the API port is private. | Default `0` |
| `BOOTSTRAP_ORG` | Your chosen organization identifier, e.g. `demo` or `bokaro-training`. Workers enter this at login. | Seed command; default `demo` |
| `BOOTSTRAP_ADMIN_PHONE` | Actual administrator phone in E.164 format | Required by the seed command |

Generate each server secret separately:

```powershell
node -e "process.stdout.write(require('node:crypto').randomBytes(48).toString('hex'))"
```

Run it once for `JWT_SECRET` and again for `OTP_PEPPER`. Copy each output into its matching `.env` line. Do not use the same value for both. The setup helper already performs this generation without printing secrets.

Example database URL structure:

```text
postgresql://USERNAME:PASSWORD@HOST:5432/DATABASE
```

URL-encode special characters in passwords when constructing a connection string manually. The helper generates hexadecimal passwords to avoid this problem. A Docker-internal host is `db`; a database published to the Windows host is `127.0.0.1`. These are not interchangeable.

## 5. Real SMS using Twilio

Create/configure a Twilio account, obtain an SMS sender or Messaging Service, and copy the account SID/auth token into the backend environment. Twilio's [Messages API](https://www.twilio.com/docs/messaging/api/message-resource) documents the account, recipient, body, and sender parameters used by this implementation.

```dotenv
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=AC_REPLACE_WITH_YOUR_ACCOUNT_SID
TWILIO_AUTH_TOKEN=REPLACE_WITH_YOUR_AUTH_TOKEN
TWILIO_FROM=REPLACE_WITH_YOUR_CONFIGURED_SENDER
TWILIO_MESSAGING_SERVICE_SID=
```

Those `REPLACE` strings are explanations, not working credentials. Use a sender that is enabled for your recipients' country. Complete the provider's sender/recipient setup before testing delivery. If a provider rejects an SMS, inspect its delivery logs; the app never treats a failed provider request as a successful login.

The API creates the six-digit OTP and sends it through the Messages API. This integration does not use Twilio Verify, so a Verify Service SID is not one of these variables.

### Alternative: a different SMS provider via webhook

Your bridge must accept:

```http
POST /your-sms-endpoint
Authorization: Bearer YOUR_SMS_WEBHOOK_TOKEN
Content-Type: application/json

{"phone":"+91...","code":"123456","expiresInSeconds":300}
```

It should authenticate the bearer token, call your provider using the approved sender/template, and return a success status only when that provider accepts the message. Configure the bridge URL and token in the API. Never point the bridge back to `/api/auth/request`; that would recurse.

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
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml run --rm -e BOOTSTRAP_ORG=YOUR_ORG -e BOOTSTRAP_ADMIN_PHONE=YOUR_E164_NUMBER api node src/seed.js
docker compose --env-file deploy/.env.production -f deploy/compose.production.yaml up -d
```

Replace `YOUR_ORG` and `YOUR_E164_NUMBER` before running the seed command. The gateway handles HTTPS for the configured domain. Only the gateway exposes public ports; the API/database remain private. Arrange database backups, restoration checks, secret storage, and operational monitoring for your deployment.

## 9. Troubleshooting

| Symptom | Check |
|---|---|
| Dark login background | Install the rebuilt APK. The updated code forces a light Android window, Compose surface, input backgrounds, and system bars. |
| Can't connect on emulator | API running on computer, APK built with `http://10.0.2.2:8080/`, port 8080 available |
| Can't connect on a physical phone | A reachable trusted HTTPS URL is required; `10.0.2.2` is emulator-only |
| No local OTP file | Request a code for an existing employee in the correct organization; distinguish `local` sandbox from `demo` PostgreSQL setup |
| Invalid OTP | Use latest request, six digits, within five minutes; after five wrong attempts request a new code |
| `JWT_SECRET` / `OTP_PEPPER` startup error | Both must be at least 32 characters and different; regenerate if needed |
| Database relation missing | Run `npm run migrate` against the same database the API uses |
| Bootstrap phone error | Enter a real E.164 value beginning with `+`; remove placeholder text |
| Firebase not configured message | Supply all four Android properties, configure server project/credentials, rebuild, then enable notifications |
| Certificate not issued after passing | A different trainer/safety officer must approve practical competence in **Assessments** |
| Job assignment blocked | The worker needs valid, unrevoked certificates covering the entire shift and must have no conflicting shift or approved leave |
| Offline attendance not in payroll records | It remains a claim until a supervisor/HR/admin reviews it |
| Environment change seems ignored | Restart the regular API after `.env` changes; rebuild Android after Gradle property changes; sandbox runner ignores `.env` |

