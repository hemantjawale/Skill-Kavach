import express from "express";
import helmet from "helmet";
import { rateLimit } from "express-rate-limit";
import { z } from "zod";
import { randomUUID } from "node:crypto";
import { authentication, publicUser } from "./auth.js";
import { applyOperation, need, eligibility } from "./domain.js";
import { publicModules } from "./catalog.js";
export function createApp(db, config) {
  need(
    config.jwtSecret?.length >= 32 && config.otpPepper?.length >= 32,
    "Configure independent secrets of at least 32 characters.",
    500,
  );
  need(
    config.jwtSecret !== config.otpPepper,
    "JWT and OTP secrets must differ.",
    500,
  );
  const app = express(),
    auth = authentication(db, config);
  app.disable("x-powered-by");
  if (config.proxyHops) app.set("trust proxy", config.proxyHops);
  app.use(helmet());
  app.use(express.json({ limit: "64kb" }));
  app.use((req, res, next) => {
    res.set("Cache-Control", "no-store");
    next();
  });
  app.use(
    "/api",
    rateLimit({
      windowMs: 60000,
      limit: 120,
      standardHeaders: "draft-8",
      legacyHeaders: false,
    }),
  );
  app.get("/health", async (req, res) => {
    await db.query("SELECT 1");
    res.json({ status: "ok" });
  });
  const authLimit = rateLimit({
    windowMs: 15 * 60000,
    limit: 30,
    standardHeaders: "draft-8",
    legacyHeaders: false,
  });
  app.post("/api/auth/request", authLimit, async (req, res) =>
    res.json(await auth.request(req.body)),
  );
  app.post("/api/auth/verify", authLimit, async (req, res) =>
    res.json(await auth.verify(req.body)),
  );
  app.post("/api/auth/refresh", authLimit, async (req, res) =>
    res.json(await auth.refresh(req.body)),
  );
  app.get("/api/certificates/:id/verify", async (req, res) => {
    const cid = z.uuid().parse(req.params.id);
    const c = (
      await db.query(
        "SELECT data FROM records WHERE id=$1 AND kind='certificate'",
        [cid],
      )
    ).rows[0];
    if (!c) return res.status(404).json({ status: "INVALID" });
    res.json({
      id: cid,
      moduleId: c.data.moduleId,
      status:
        c.data.status === "REVOKED"
          ? "REVOKED"
          : new Date(c.data.expiresAt) <= new Date()
            ? "EXPIRED"
            : "VALID",
      issuedAt: c.data.issuedAt,
      expiresAt: c.data.expiresAt,
    });
  });
  app.use("/api", auth.middleware);
  app.post("/api/auth/logout", async (req, res) => {
    await db.query("UPDATE sessions SET revoked=true WHERE id=$1", [
      req.sessionId,
    ]);
    res.json({ ok: true });
  });
  app.get("/api/bootstrap", async (req, res) => {
    const u = req.user;
    let rs = (
      await db.query(
        "SELECT * FROM records WHERE org_id=$1 ORDER BY updated_at DESC",
        [u.org_id],
      )
    ).rows;
    const orgWide = ["ORG_ADMIN", "HR"].includes(u.role);
    rs = rs.filter((r) =>
      u.role === "WORKER"
        ? r.owner_id === u.id
        : orgWide || r.data.site === u.site,
    );
    rs = rs.filter(
      (r) =>
        r.kind !== "payroll" ||
        r.owner_id === u.id ||
        ["HR", "ORG_ADMIN"].includes(u.role),
    );
    rs = rs.filter((r) => r.kind !== "notification" || r.owner_id === u.id);
    const visible = {
      TRAINER: ["progress", "attempt", "certificate", "trainingAssignment"],
      SAFETY_OFFICER: [
        "progress",
        "attempt",
        "certificate",
        "trainingAssignment",
        "job",
        "emergency",
      ],
      SUPERVISOR: [
        "progress",
        "attempt",
        "certificate",
        "trainingAssignment",
        "job",
        "attendance",
        "attendanceClaim",
        "leave",
        "emergency",
      ],
      SITE_ADMIN: [
        "progress",
        "attempt",
        "certificate",
        "trainingAssignment",
        "job",
        "attendance",
        "attendanceClaim",
        "emergency",
      ],
    };
    if (visible[u.role])
      rs = rs.filter(
        (r) => r.owner_id === u.id || visible[u.role].includes(r.kind),
      );
    const allWorkers =
      u.role === "WORKER"
        ? []
        : (
            await db.query(
              "SELECT * FROM users WHERE org_id=$1 AND active=true",
              [u.org_id],
            )
          ).rows
            .filter((w) => orgWide || w.site === u.site)
            .map(publicUser);
    res.json({
      user: publicUser(u),
      modules: publicModules(),
      records: rs,
      workers: allWorkers,
      serverTime: new Date().toISOString(),
      verificationBase: `${config.publicUrl}/verify/`,
      leaveAllowance: { CASUAL: 12, SICK: 12 },
      compliance: allWorkers.map((w) => ({
        workerId: w.id,
        name: w.name,
        jobs: rs
          .filter((r) => r.kind === "job" && r.owner_id === w.id)
          .map((j) => ({
            jobId: j.id,
            missing: eligibility(rs, w.id, j.data),
          })),
      })),
    });
  });
  app.post("/api/devices", async (req, res) => {
    const p = z
      .object({ token: z.string().min(20).max(4096) })
      .strict()
      .parse(req.body);
    await db.query(
      "INSERT INTO device_tokens(token,user_id,session_id) VALUES($1,$2,$3) ON CONFLICT(token) DO UPDATE SET user_id=EXCLUDED.user_id,session_id=EXCLUDED.session_id,updated_at=now()",
      [p.token, req.user.id, req.sessionId],
    );
    res.json({ ok: true });
  });
  app.post("/api/operations", async (req, res) =>
    res.json(await applyOperation(db, req.user, req.body)),
  );
  app.post("/api/workers", async (req, res) => {
    const u = req.user;
    need(
      u.role === "ORG_ADMIN",
      "Organization administrator permission required.",
      403,
    );
    const p = z
      .object({
        employeeId: z.string().trim().min(1).max(50),
        name: z.string().trim().min(1).max(100),
        phone: z.string().regex(/^\+[1-9]\d{7,14}$/),
        role: z.enum([
          "WORKER",
          "SUPERVISOR",
          "TRAINER",
          "SAFETY_OFFICER",
          "HR",
          "SITE_ADMIN",
          "ORG_ADMIN",
        ]),
        site: z.string().trim().min(1).max(100),
      })
      .strict()
      .parse(req.body);
    const uid = randomUUID();
    await db.transaction(async (tx) => {
      await tx.query(
        "INSERT INTO users(id,org_id,employee_id,name,phone,role,site) VALUES($1,$2,$3,$4,$5,$6,$7)",
        [uid, u.org_id, p.employeeId, p.name, p.phone, p.role, p.site],
      );
      await tx.query(
        "INSERT INTO audit_log(id,org_id,actor_id,action,record_id) VALUES($1,$2,$3,$4,$5)",
        [randomUUID(), u.org_id, u.id, "worker.create", uid],
      );
    });
    res.status(201).json({ id: uid });
  });
  app.get("/api/audit", async (req, res) => {
    need(
      req.user.role === "ORG_ADMIN",
      "Administrator permission required.",
      403,
    );
    res.json(
      (
        await db.query(
          "SELECT * FROM audit_log WHERE org_id=$1 ORDER BY created_at DESC LIMIT 200",
          [req.user.org_id],
        )
      ).rows,
    );
  });
  app.use("/api", (req, res) =>
    res.status(404).json({ error: "Endpoint not found." }),
  );
  if (config.adminPath) {
    app.use(express.static(config.adminPath));
    app.get(["/", "/manager", "/verify/:id"], (req, res) =>
      res.sendFile(`${config.adminPath}/index.html`),
    );
  }
  app.use((err, req, res, next) => {
    const status =
      err instanceof z.ZodError
        ? 400
        : (err.status ?? (err.code === "23505" ? 409 : 500));
    const message =
      err instanceof z.ZodError
        ? "Check the submitted fields and try again."
        : err.code === "23505"
          ? "This record already exists."
          : status < 500
            ? err.message
            : "Unable to complete the request. Please retry.";
    if (status >= 500) console.error("Request failed", err.code ?? err.name);
    res.status(status).json({ error: message });
  });
  return app;
}
