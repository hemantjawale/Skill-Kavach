# Mobile, manager console and AR testing

## Deploy this update first (Node.js on Render)

The current hosted root returned HTTP 404 during review: the backend is running, but the console is not built. Push these code changes to the deployment branch, then update the existing Render Node service:

| Setting | Value |
|---|---|
| Root Directory | Leave empty (remove `backend`) |
| Build Command | `npm --prefix admin install --include=dev && npm --prefix admin run build && npm --prefix backend install --omit=dev` |
| Start Command | `cd backend && npm run migrate && npm start` |
| Health Check Path | `/health` |

Keep the existing database, CA secret file, SMS and Firebase environment values. The administrator is already seeded; do not change accounts or re-seed. Manually deploy the latest commit. After it succeeds, open **https://skill-kavach.onrender.com/manager**. The root URL also opens the manager console. This replaces the website that used to be served at localhost:8080.

Deploy the backend before installing this APK: older backend versions reject the new phone and portal fields. No new schema migration is needed for these fields; the normal migration remains safe to run.

## Mobile login and employee numbers

Enter your organization ID (currently `demo` if unchanged on Render), employee ID (`ADMIN` for the bootstrap administrator), and registered mobile number including country code, such as `+91` followed by ten digits. Enter digits without spaces.

The submitted number must match that employee's registered number. A mismatch gets the same generic response but sends no SMS and cannot produce a session. Login does not create an account or change its phone number. This prevents someone entering `ADMIN` with their own number and obtaining administrator access. Older mobile clients can still request OTP using their existing account fields; SMS always goes to the stored account number.

OTP was already addressed to each employee's registered phone, not globally to the bootstrap number. To test another employee:

1. Sign into the manager console as `ADMIN` with the bootstrap administrator's registered number.
2. Open **Workers → Add employee**. Enter employee ID, name, their actual phone, site and `WORKER` role.
3. On that employee's phone, enter the same organization, their employee ID and phone. The code goes to their number.
4. If Twilio does not deliver, inspect its delivery logs and account/destination permissions. The app cannot bypass provider restrictions.

## Separate manager login and existing features

The mobile sign-in screen has **Manager login — workforce console**, opening `/manager` in the browser. This is a staff portal: worker accounts cannot get an OTP through its manager login flow. Server-side role and site checks still control every operation; opening this URL does not grant manager permissions.

Use `ADMIN` initially. In **Workers → Add employee**, provision separate staff accounts with their own employee IDs and phone numbers. Choose `ORG_ADMIN` only for someone authorized to administer the whole organization; choose `HR`, `SUPERVISOR`, `TRAINER` or the other scoped roles for limited duties. There is no shared manager password or automatic privilege upgrade.

Already implemented:

- **Payroll:** HR/organization administrators can publish base salary, overtime, incentives, deductions and payment status. Values entered in INR are stored in integer paise. Publishing the same worker/month updates that payroll record. Workers see their own published payslips in the mobile app. This records payroll; it does not transfer money.
- **Leave:** workers submit leave; authorized staff review and approve/reject it.
- **Training:** assign modules and due dates. The new employee training-history section shows saved, synchronized completions with mode, duration and completion time. Unsaved local steps are not a live manager feed.
- **Assessments/certificates:** view attempts, perform independent practical review, issue and revoke qualifications.
- **Jobs/tasks/attendance:** assign eligible workers, review submitted tasks and offline attendance claims.

There is not universal CRUD for every entity. For example, employee edit/deactivation and payroll deletion are not exposed in the current console. Payroll correction uses the existing worker/month update. Do not treat the console as a full HR/ERP integration.

## AR: getting the marks to appear

Use an ARCore-supported physical Android phone with Google Play Services for AR installed and camera permission allowed. Practice mode has buttons and intentionally has no camera tracking or placement ring.

1. Sign in, open **Training**, select a module and choose **Start AR training**.
2. Point the camera down at an unobstructed, well-lit, textured floor. Slowly move the phone sideways so AR can recognize the surface. Plain shiny floors and low light can prevent detection.
3. The center ring is **red while scanning** and **green when the center points at a detected floor**. A red ring is not a detected hazard.
4. When green, tap **Place equipment**, or tap a detected floor in the camera view. Virtual equipment and labels now appear. Nothing appears automatically merely from opening the camera.
5. Keep roughly **0.4–4 metres horizontally from the placement point**, aim back at it and follow the current instruction. Red extinguisher/hazard equipment and a green exit are virtual training objects, not automatic identification of real equipment.
6. Tap the labelled target requested by the instruction. For the **Sweep** target, drag sideways and finish on its label. A wrong target does not advance the step.
7. If equipment is off-screen or tracking is lost, look back at the training area or use **Reposition**, scan until green, then **Place equipment** again. Reposition moves the scene; it does not reset lesson progress.
8. Finish the sequence and tap **Save training** after at least 30 seconds. When online it synchronizes; offline it queues for later. Complete the assessment and an independent practical review for certification.

The AR renderer does not recognize real fires, gas, machines, hazards or safe zones. Test in a clear training space without live hazards. If your device is unsupported, use the explicitly labelled practice mode. Guest practice does not save a qualification to a worker account.

## Quick acceptance checks

- Worker phone login sends to the correct registered number; a mismatched number does not authenticate.
- Worker credentials cannot log in through the manager portal.
- Create a worker, publish salary, submit/review leave, save training and view its history as staff.
- Confirm the red-to-green placement ring and equipment on an AR-supported physical phone, then reposition and complete a lesson.

Backend tests cover phone matching, staff login gating, salary correction and authorization. Android build/unit tests and lint validate code; physical-device AR tracking and live SMS delivery still require your device/provider test.
