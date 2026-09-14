import {
  randomBytes,
  randomInt,
  randomUUID,
  createHash,
  createHmac,
  timingSafeEqual,
} from "node:crypto";
import { SignJWT, jwtVerify } from "jose";
import { z } from "zod";
import { need } from "./domain.js";
export const hash = (value) => createHash("sha256").update(value).digest("hex");
export function authentication(db, config) {
  const key = new TextEncoder().encode(config.jwtSecret);
  const digest = (challenge, code) =>
    createHmac("sha256", config.otpPepper)
      .update(`${challenge}:${code}`)
      .digest("hex");
  const tokens = async (tx, user, sid = randomUUID()) => {
    const refreshToken = randomBytes(48).toString("base64url");
    await tx.query(
      "INSERT INTO sessions(id,user_id,refresh_hash,expires_at) VALUES($1,$2,$3,$4)",
      [sid, user.id, hash(refreshToken), new Date(Date.now() + 7 * 86400000)],
    );
    const accessToken = await new SignJWT({ sid })
      .setProtectedHeader({ alg: "HS256" })
      .setSubject(user.id)
      .setIssuer("suraksha-setu")
      .setAudience("suraksha-clients")
      .setIssuedAt()
      .setExpirationTime("15m")
      .sign(key);
    return { accessToken, refreshToken, user: publicUser(user) };
  };
  return {
    async request(body) {
      const p = z
        .object({
          organization: z.string().min(1).max(100),
          employeeId: z.string().min(1).max(100),
          phone: z
            .string()
            .regex(/^\+[1-9]\d{7,14}$/)
            .optional(),
          portal: z.enum(["worker", "manager"]).optional(),
        })
        .strict()
        .parse(body);
      const candidate = (
        await db.query(
          "SELECT * FROM users WHERE org_id=$1 AND employee_id=$2 AND active=true",
          [p.organization, p.employeeId],
        )
      ).rows[0];
      const user =
        candidate &&
        (!p.phone || candidate.phone === p.phone) &&
        (p.portal !== "manager" || candidate.role !== "WORKER")
          ? candidate
          : null;
      const challengeId = randomUUID(),
        code = String(randomInt(100000, 1000000));
      await db.transaction(async (tx) => {
        const throttle = await tx.query(
          "INSERT INTO auth_throttles(identity_hash,requested_at) VALUES($1,now()) ON CONFLICT(identity_hash) DO UPDATE SET requested_at=now() WHERE auth_throttles.requested_at < now() - interval '60 seconds' RETURNING identity_hash",
          [digest("identity", JSON.stringify([p.organization, p.employeeId]))],
        );
        need(
          throttle.rows.length > 0,
          "Please wait one minute before requesting another code.",
          429,
        );
        if (user) {
          await tx.query("SELECT id FROM users WHERE id=$1 FOR UPDATE", [
            user.id,
          ]);
          await tx.query(
            "UPDATE otp_challenges SET consumed=true WHERE user_id=$1",
            [user.id],
          );
        }
        await tx.query(
          "INSERT INTO otp_challenges(id,user_id,digest,expires_at) VALUES($1,$2,$3,$4)",
          [
            challengeId,
            user?.id ?? null,
            digest(challengeId, code),
            new Date(Date.now() + 5 * 60000),
          ],
        );
      });
      if (user) await config.sendOtp(user.phone, code, challengeId);
      return {
        challengeId,
        message:
          "If the account and phone match, a code has been sent to the registered number.",
      };
    },
    async verify(body) {
      const p = z
        .object({ challengeId: z.uuid(), code: z.string().regex(/^\d{6}$/) })
        .strict()
        .parse(body);
      const result = await db.transaction(async (tx) => {
        const c = (
          await tx.query(
            "SELECT * FROM otp_challenges WHERE id=$1 FOR UPDATE",
            [p.challengeId],
          )
        ).rows[0];
        if (
          !c ||
          c.consumed ||
          c.attempts >= 5 ||
          new Date(c.expires_at) < new Date()
        )
          return null;
        await tx.query(
          "UPDATE otp_challenges SET attempts=attempts+1 WHERE id=$1",
          [c.id],
        );
        if (
          !c.user_id ||
          !timingSafeEqual(
            Buffer.from(c.digest, "hex"),
            Buffer.from(digest(c.id, p.code), "hex"),
          )
        )
          return null;
        await tx.query("UPDATE otp_challenges SET consumed=true WHERE id=$1", [
          c.id,
        ]);
        const u = (
          await tx.query("SELECT * FROM users WHERE id=$1 AND active=true", [
            c.user_id,
          ])
        ).rows[0];
        return u ? tokens(tx, u) : null;
      });
      need(result, "Code invalid or expired. Request a new code.", 401);
      return result;
    },
    async refresh(body) {
      const p = z
        .object({ refreshToken: z.string().min(40).max(200) })
        .strict()
        .parse(body);
      const result = await db.transaction(async (tx) => {
        const s = (
          await tx.query(
            "SELECT * FROM sessions WHERE refresh_hash=$1 FOR UPDATE",
            [hash(p.refreshToken)],
          )
        ).rows[0];
        if (!s) return null;
        if (s.revoked) {
          await tx.query("UPDATE sessions SET revoked=true WHERE user_id=$1", [
            s.user_id,
          ]);
          return null;
        }
        if (new Date(s.expires_at) < new Date()) return null;
        await tx.query("UPDATE sessions SET revoked=true WHERE id=$1", [s.id]);
        const u = (
          await tx.query("SELECT * FROM users WHERE id=$1 AND active=true", [
            s.user_id,
          ])
        ).rows[0];
        return u ? tokens(tx, u) : null;
      });
      need(result, "Session expired. Sign in again.", 401);
      return result;
    },
    async middleware(req, res, next) {
      try {
        const token = req.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
        need(token, "Sign in to continue.", 401);
        const { payload } = await jwtVerify(token, key, {
          algorithms: ["HS256"],
          issuer: "suraksha-setu",
          audience: "suraksha-clients",
        });
        const u = (
          await db.query(
            "SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE u.id=$1 AND s.id=$2 AND s.revoked=false AND s.expires_at>now() AND u.active=true",
            [payload.sub, payload.sid],
          )
        ).rows[0];
        need(u, "Session expired. Sign in again.", 401);
        req.user = u;
        req.sessionId = payload.sid;
        next();
      } catch {
        res.status(401).json({ error: "Session expired. Sign in again." });
      }
    },
  };
}
export const publicUser = (u) => ({
  id: u.id,
  organization: u.org_id,
  employeeId: u.employee_id,
  name: u.name,
  role: u.role,
  site: u.site,
});
