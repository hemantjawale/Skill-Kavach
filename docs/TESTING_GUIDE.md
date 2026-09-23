# Mobile, manager console and AR testing

## Deploy this update first

Follow [the SMTP Render migration guide](../deploy/RENDER.md), including the additive schema migration, administrator email setup, Node build settings and SMTP hosting requirement. Deploy backend first, then install the email-login APK.

## Email login and manager workflows

Follow [README login instructions](../README.md#email-otp-login-website-and-android) and [the SMTP migration guide](../deploy/RENDER.md). Each employee needs a stored email; manager email updates revoke old sessions and codes. Mobile self-registration remains pending until an authorized reviewer approves it.

The console includes salary publish/update (HR/admin), leave review, training assignments/history, assessments, certificates, tasks and attendance. Manager access uses staff roles, not shared credentials. Payroll is a record, not a bank transfer.

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

- Worker email login sends to the correct registered email; a mismatched email does not authenticate.
- Worker credentials cannot log in through the manager portal.
- Create a worker, publish salary, submit/review leave, save training and view its history as staff.
- Confirm the red-to-green placement ring and equipment on an AR-supported physical phone, then reposition and complete a lesson.

Backend tests cover phone matching, staff login gating, salary correction and authorization. Android build/unit tests and lint validate code; physical-device AR tracking and live email delivery still require your device/provider test.
