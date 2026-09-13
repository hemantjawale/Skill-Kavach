import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";

/** Durable, at-least-once delivery. Inbox data remains authoritative when push is unavailable. */
export function startPushWorker(db, projectId) {
  if (!projectId) return () => {};
  const firebase = initializeApp({
    credential: applicationDefault(),
    projectId,
  });
  let running = false;
  const timer = setInterval(async () => {
    if (running) return;
    running = true;
    try {
      const job = await db.transaction(async (tx) => {
        const row = (
          await tx.query(
            "SELECT notification_id FROM push_jobs WHERE delivered_at IS NULL AND attempts < 12 AND next_attempt <= now() ORDER BY next_attempt LIMIT 1 FOR UPDATE SKIP LOCKED",
          )
        ).rows[0];
        if (row)
          await tx.query(
            "UPDATE push_jobs SET next_attempt=now()+interval '5 minutes' WHERE notification_id=$1",
            [row.notification_id],
          );
        return row;
      });
      if (!job) return;
      const notification = (
        await db.query(
          "SELECT * FROM records WHERE id=$1 AND kind='notification'",
          [job.notification_id],
        )
      ).rows[0];
      const tokens = (
        await db.query(
          "SELECT d.token FROM device_tokens d JOIN sessions s ON s.id=d.session_id JOIN users u ON u.id=d.user_id WHERE d.user_id=$1 AND s.revoked=false AND s.expires_at>now() AND u.active=true",
          [notification.owner_id],
        )
      ).rows;
      if (!tokens.length) {
        await db.query(
          "UPDATE push_jobs SET next_attempt=now()+interval '1 hour',last_error='NO_ACTIVE_DEVICE' WHERE notification_id=$1",
          [job.notification_id],
        );
        return;
      }
      let failed = false;
      let delivered = 0;
      for (const { token } of tokens) {
        try {
          await getMessaging(firebase).send({
            token,
            data: { notificationId: notification.id },
            android: { priority: notification.data.title.startsWith('Emergency') ? "high" : "normal", ttl: 3600000 },
          });
          delivered++;
        } catch (e) {
          if (
            [
              "messaging/registration-token-not-registered",
              "messaging/invalid-registration-token",
            ].includes(e.code)
          )
            await db.query("DELETE FROM device_tokens WHERE token=$1", [token]);
          else failed = true;
        }
      }
      if (failed)
        await db.query(
          "UPDATE push_jobs SET attempts=attempts+1,next_attempt=now()+interval '5 minutes',last_error='PROVIDER_FAILURE' WHERE notification_id=$1",
          [job.notification_id],
        );
      else if(delivered > 0)
        await db.query(
          "UPDATE push_jobs SET delivered_at=now(),last_error=NULL WHERE notification_id=$1",
          [job.notification_id],
        );
      else await db.query("UPDATE push_jobs SET next_attempt=now()+interval '1 hour',last_error='NO_ACTIVE_DEVICE' WHERE notification_id=$1",[job.notification_id]);
    } catch {
      console.error("Push delivery cycle failed");
    } finally {
      running = false;
    }
  }, 2000);
  timer.unref();
  return () => clearInterval(timer);
}
