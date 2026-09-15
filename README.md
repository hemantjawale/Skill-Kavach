# SurakshaSetu

Android safety training and workforce management application built from the requirements in `AI_INSTRUCTIONS`. The repository now includes the Kotlin/Compose worker app, ARCore training renderer, Express API, PostgreSQL schema, React staff console, local development runner, and deployment configuration.

**Start with [ENV_SETUP.md](ENV_SETUP.md)** for complete local setup and the source of every environment variable.

## What to enter on the website and mobile app

Use the deployed [manager website](https://skill-kavach.onrender.com/manager) and the latest Android APK. The APK connects to the hosted backend; no local server is needed.

| Where / account | Organization ID | Employee ID | Mobile number |
|---|---|---|---|
| Manager website — first administrator | `demo`* | `ADMIN` | The actual phone used as `BOOTSTRAP_ADMIN_PHONE` when the administrator was seeded |
| Mobile APK — initial administrator test | `demo`* | `ADMIN` | The same administrator's registered phone |
| Mobile APK — worker | `demo`* | The employee ID created for that worker, e.g. `EMP001` | That worker's registered phone |
| Manager website — another staff member | `demo`* | Their separately created staff employee ID | That staff member's registered phone |

\* `demo` is the configured local bootstrap organization and default. If you set a different `BOOTSTRAP_ORG` on Render when seeding, use that organization ID instead. IDs must match the saved account, including case: `ADMIN` is uppercase. The field is **Employee ID**, not an employer name or email address. Changing bootstrap environment values later does not rename an existing account or update its phone.

Enter phone numbers with country code and without spaces, for example `+91` followed by the ten-digit Indian mobile number. Use your real registered number, not an example number. Click **Send verification code**, then enter the six-digit SMS code. The organization, employee ID and phone must match; entering a different number does not redirect an account's OTP.

### Create a worker for a complete mobile test

1. Log into the website using `demo` / `ADMIN` and the administrator's registered mobile number (substitute your actual organization if different).
2. Open **Workers → Add employee**.
3. Set Employee ID to `EMP001`, enter the worker's name, their real phone number and their site, then choose role **WORKER** and save. `EMP001` is an example you create, not an automatically seeded account.
4. On the mobile APK, enter `demo` / `EMP001` and that worker's phone. Request and verify the SMS code.
5. Use this worker account for training, leave and payslip tests. Use the administrator website to assign training, review leave and publish salary.

To create another manager, add a separate employee with an appropriate staff role. `ORG_ADMIN` has organization administration access; `HR` handles payroll/leave; other roles have narrower permissions. Worker accounts cannot use the manager login. A role is assigned by an authorized administrator, not selected to gain access during login.

If no OTP arrives, confirm the saved organization/employee/phone values and check Twilio delivery logs. A generic response intentionally does not reveal whether an account exists. Wait one minute before requesting another code.

## How to use AR — worked fire-training example

Install the latest APK containing the placement ring and buttons. Use a physical ARCore-supported phone, install/update Google Play Services for AR if prompted, and allow camera permission. Choose a clear training space with good lighting and a textured floor; do not light a real fire or use live hazards.

1. Sign in as your worker, e.g. `EMP001`.
2. Open **Training → Fire & Explosion Response → Start AR training**. Do not choose offline practice if you want camera AR.
3. Point the phone at the floor roughly one to two metres ahead. Slowly move it sideways while keeping the floor in view.
4. Look at the center ring: **red means keep scanning; green means the floor under the ring is ready for placement**. This ring does not detect danger or safety in the real scene.
5. When green, tap **Place equipment**. Labelled virtual equipment should appear, including a green exit and red equipment. Keep the camera pointed at the placement area and stay approximately 0.4–4 metres horizontally from it.
6. Follow the eight steps below. Tap the on-screen labels; you do not need real equipment for this app interaction test.

| Step shown by the app | Action in this virtual scenario |
|---|---|
| Identify the exit | Tap **Exit** |
| Raise the alarm | Tap **Alarm** |
| Choose the equipment | Tap **Extinguisher** |
| Pull | Tap **Pin** |
| Aim | Tap **Base** |
| Squeeze | Tap **Handle** |
| Sweep | Drag sideways across the screen and finish on the **Sweep** label; a simple tap does not complete this step |
| Evacuate | Tap **Exit** again |

7. The app should show **Correct** after each correct selection and advance to the next step. A wrong selection does not advance.
8. At the end, tap **Save training**. Spend at least 30 seconds reviewing the sequence before saving. Online progress synchronizes; offline submissions queue for later.
9. On the manager website, open **Training → Employee training history** to see the saved completion after synchronization. The worker must also complete the assessment and receive an independent practical review before certification.

### If you cannot see any marks or equipment

- **Practice mode:** it shows target buttons, not AR marks. Reopen the module using **Start AR training**.
- **Red ring stays red:** aim at a well-lit, textured floor and move slowly. A blank/shiny floor, darkness, or a wall may not provide a usable horizontal surface.
- **Green ring but no equipment:** tap **Place equipment**. Opening AR alone does not place the scene.
- **Equipment disappeared:** aim back at the original placement area, check the tracking message, and stay within the stated distance. Use **Reposition**, scan until green and place again if needed. Reposition does not reset completed steps.
- **Camera permission/AR services unavailable:** follow the displayed permission/install prompt. Unsupported devices can use practice mode but cannot test tracked AR.
- **No ring or placement buttons at all:** ensure you installed the newly rebuilt APK rather than the previous version.

This is a virtual procedure simulation. It does not recognize real fires, gas leaks or real equipment, and completing it alone does not authorize work. Physical-device tracking and live SMS delivery still need testing on your setup.

For the Render build settings and further manager workflows, see [the deployment and testing guide](docs/TESTING_GUIDE.md).

## Run the local system

```powershell
Set-Location 'D:\Skill Kavach\admin'
npm.cmd ci
npm.cmd run build
Set-Location 'D:\Skill Kavach\backend'
npm.cmd ci
node scripts/dev-local.js
```

Open `http://localhost:8080`. Organization: `local`. Employee IDs: `ADMIN`, `WORKER`, `TRAINER`. After requesting a code, read `backend/.data/otp.json`. These are sandbox identities with local OTP delivery, not production accounts.

For an Android emulator:

```powershell
Set-Location 'D:\Skill Kavach'
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8080/
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. A physical phone needs a configured reachable HTTPS API.

## Implemented flows

- Light-only Android UI, including onboarding, editable login fields and system bars.
- Organization/employee OTP authentication, short-lived JWT access tokens, rotating refresh tokens, logout revocation and server-side role/site boundaries.
- Fire response and gas/confined-space training: ARCore installation/permission handling, floor hit testing, anchored procedural 3D equipment, labelled interactions, PASS sweep gesture, spoken English instructions and explicit non-AR practice mode.
- Bundled offline training/questions/equipment, encrypted saved step progress, encrypted Room snapshots/outbox, retrying WorkManager synchronization and rejected-action review.
- Server-scored assessments, weak-topic results and independent trainer review before certificate issuance.
- QR certificate verification with current expiry/revocation status; public verification omits worker personal data.
- Qualification checks covering the full shift, conflicting-shift/leave checks, job assignment, task submissions and staff verification.
- Server-authorized check-in/out, working duration, and separately reviewed offline attendance claims.
- Leave balances/requests/decisions; payroll publishing and worker payslip views.
- Explicitly queued/received/acknowledged SOS states, offline instructions, a notification inbox and optional Firebase push delivery.
- Staff console for workers, training assignment, assessment review, certificates, jobs, tasks, attendance, leave, payroll, emergencies and audit events.
- PostgreSQL transactions, replay-safe operation IDs, input validation, rate limits, security headers, CI and Docker/Caddy deployment files.

## Architecture decisions

The written brief specifies Express while the diagram says NestJS; this implementation uses Express. It uses parameterized PostgreSQL SQL rather than Prisma. `records` is a tenant-scoped JSONB aggregate store with separate indexed users, organizations, OTPs, sessions, audit, idempotency and push tables. Organization row locks serialize safety-sensitive decisions. This favors a small, auditable initial deployment; high-volume deployments need load testing, pagination and finer-grained data/locking boundaries.

The API is authoritative for certification, authorization and payroll. Client AR telemetry is not trusted as proof of competence. A reviewer cannot approve their own assessment. The device never issues its own usable certificate. Offline attendance is a claim, not a retroactive safety clearance.

All current equipment geometry is generated locally by the renderer. There are no placeholder download buttons or dependencies on unlicensed downloaded models. The training is a guided spatial simulation, not computer-vision hazard recognition or real gas detection.

## Validation

```powershell
Set-Location backend
npm.cmd test
npm.cmd audit --omit=dev --audit-level=high
Set-Location ../admin
npm.cmd run build
Set-Location ..
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PAPI_BASE_URL=http://10.0.2.2:8080/
```

Backend tests execute against PGlite's PostgreSQL engine; they cover OTP replay/lockout, credential rotation, tenant/site isolation, private payroll access, assessment prerequisites, certificate review/revocation, assignment gating, operation replay conflicts, leave limits, offline attendance review, notifications and SMS provider configuration.

Android JVM tests exercise cached credential expiry and full-shift validity. Instrumentation tests cover Keystore tamper detection, Room account isolation, and the light theme under a night-mode configuration. Instrumentation and AR camera validation require a connected Android device/emulator; compiling them is not the same as running them.

Browser checks exercised local OTP login, dashboard layout, job creation and rejection of an unqualified worker assignment.

## Field-release work requiring deployment/content inputs

This is a working implementation, **not a claim of a security audit or completed field certification**. Before operational deployment:

1. Configure the real HTTPS domain, database, SMS sender, administrator identities, signing key and optional Firebase credentials. Real SMS/push delivery cannot be verified with empty service credentials.
2. Validate AR tracking, placement, touch accuracy, camera loss, resume, denied permissions, battery use and performance on the actual supported devices. No physical AR device was connected during implementation.
3. Obtain site safety-officer approval for scenarios and assessment content. The bundled scenarios are generic examples, not a substitute for site procedures or an entry permit.
4. Supply reviewed Hindi/Santali safety packs and choose the Santali script. The current build provides English content and Hindi bottom-navigation labels; it does not claim complete Hindi or Santali localization. Santali's missing pack is disclosed in language selection.
5. Validate the organization's leave/certificate policies. The current explicit defaults are 12 days per leave type per year and a 365-day internal training certificate.
6. Conduct deployment security review, device tests, backup restoration tests, scale/load tests and emergency-response operational acceptance.

The broader brief also describes photo/video evidence and attachments, remote content authoring/storage, richer industrial models/physics, QR/geofenced attendance, ERP integration, configurable organization/site administration and expanded analytics. Those extensions are not implemented in this revision. Task evidence is currently a text note, training assets are bundled, and payslips are in-app views. See the source rather than treating these as working integrations.
