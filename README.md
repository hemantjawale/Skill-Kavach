# SurakshaSetu

Android safety training and workforce management application built from the requirements in `AI_INSTRUCTIONS`. The repository now includes the Kotlin/Compose worker app, ARCore training renderer, Express API, PostgreSQL schema, React staff console, local development runner, and deployment configuration.

**Start with [ENV_SETUP.md](ENV_SETUP.md)** for complete local setup and the source of every environment variable.

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

