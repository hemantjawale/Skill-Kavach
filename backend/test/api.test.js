import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { PGlite } from "@electric-sql/pglite";
import supertest from "supertest";
import { createApp } from "../src/app.js";
import { schema } from "../src/migrate.js";
import { modules } from "../src/catalog.js";
import { applyOperation, eligibility } from "../src/domain.js";

let pg, db, api;
const codes = new Map();
const destinations = new Map();
const actors = {};
before(async () => {
  pg = new PGlite();
  await pg.exec(schema);
  db = {
    query: (q, p) => pg.query(q, p),
    transaction: (fn) => pg.transaction(fn),
  };
  await db.query("INSERT INTO organizations VALUES ('o1','One'),('o2','Two')");
  for (const [id, org, role, site] of [
    ["admin", "o1", "ORG_ADMIN", "A"],
    ["worker", "o1", "WORKER", "A"],
    ["trainer", "o1", "TRAINER", "A"],
    ["supervisor", "o1", "SUPERVISOR", "A"],
    ["other", "o2", "ORG_ADMIN", "B"],
    ["remote", "o1", "WORKER", "B"],
  ]) {
    await db.query(
      "INSERT INTO users(id,org_id,employee_id,name,email,role,site) VALUES($1,$2,$1,$1,$3,$4,$5)",
      [id, org, `${id}@example.test`, role, site],
    );
    actors[id] = (
      await db.query("SELECT * FROM users WHERE id=$1", [id])
    ).rows[0];
  }
});
beforeEach(() => {
  api = supertest(
    createApp(db, {
      jwtSecret: "a".repeat(40),
      otpPepper: "b".repeat(40),
      publicUrl: "https://example.test",
      sendOtp: async (email, code, challenge) => {
        codes.set(challenge, code);
        destinations.set(challenge, email);
      },
    }),
  );
});
after(async () => pg?.close());
async function login(who) {
  await db.query("DELETE FROM auth_throttles");
  const user = actors[who];
  await db.query("DELETE FROM otp_challenges WHERE user_id=$1", [who]);
  const start = await api
    .post("/api/auth/request")
    .send({ organization: user.org_id, employeeId: who, email: user.email })
    .expect(200);
  return (
    await api
      .post("/api/auth/verify")
      .send({
        challengeId: start.body.challengeId,
        code: codes.get(start.body.challengeId),
      })
      .expect(200)
  ).body;
}
async function op(who, type, payload, id = randomUUID()) {
  return applyOperation(db, actors[who], { id, type, payload });
}
async function qualify(moduleId = "fire", who = "worker") {
  const m = modules.find((m) => m.id === moduleId);
  await op(who, "training.complete", {
    moduleId,
    version: 1,
    steps: m.steps.map((s) => s.target),
    durationSeconds: 60,
    mode: "AR",
  });
  const attempt = await op(who, "assessment.submit", {
    moduleId,
    answers: m.questions.map((q) => q.answer),
  });
  const certificate = await op("trainer", "attempt.approve", {
    attemptId: attempt.id,
  });
  return { attempt, certificate };
}
test("OTP is one-use, and unauthenticated access is rejected", async () => {
  await api.get("/api/bootstrap").expect(401);
  const s = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "worker",
      email: actors.worker.email,
    })
    .expect(200);
  const body = {
    challengeId: s.body.challengeId,
    code: codes.get(s.body.challengeId),
  };
  await api.post("/api/auth/verify").send(body).expect(200);
  await api.post("/api/auth/verify").send(body).expect(401);
});
test("OTP incorrect-attempt counter persists and locks out at five", async () => {
  await db.query("DELETE FROM auth_throttles");
  await db.query("DELETE FROM otp_challenges WHERE user_id='worker'");
  const s = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "worker",
      email: actors.worker.email,
    })
    .expect(200);
  for (let i = 0; i < 5; i++)
    await api
      .post("/api/auth/verify")
      .send({ challengeId: s.body.challengeId, code: "000000" })
      .expect(401);
  assert.equal(
    (
      await db.query("SELECT attempts FROM otp_challenges WHERE id=$1", [
        s.body.challengeId,
      ])
    ).rows[0].attempts,
    5,
  );
  await api
    .post("/api/auth/verify")
    .send({
      challengeId: s.body.challengeId,
      code: codes.get(s.body.challengeId),
    })
    .expect(401);
});
test("assessments require training and never trust client scores", async () => {
  await assert.rejects(
    op("remote", "assessment.submit", {
      moduleId: "fire",
      answers: [0, 1, 1, 2, 1],
    }),
    /Complete the training/,
  );
  await assert.rejects(
    op("worker", "assessment.submit", {
      moduleId: "fire",
      answers: [0, 1, 1, 2, 1],
      score: 100,
    }),
  );
});
test("passing assessment requires independent practical approval", async () => {
  const { certificate, attempt } = await qualify();
  assert.equal(attempt.data.status, "AWAITING_PRACTICAL_REVIEW");
  assert.equal(certificate.data.status, "VALID");
  await assert.rejects(
    op("worker", "attempt.approve", { attemptId: attempt.id }),
    /permission/,
  );
  await assert.rejects(
    op("trainer", "attempt.approve", { attemptId: attempt.id }),
    /not awaiting/,
  );
});
test("idempotent replay returns same record; changed body conflicts", async () => {
  const id = randomUUID(),
    payload = { type: "FIRE", message: "Test alert" };
  const a = await op("worker", "emergency.raise", payload, id),
    b = await op("worker", "emergency.raise", payload, id);
  assert.equal(a.id, b.id);
  await assert.rejects(
    op("worker", "emergency.raise", { ...payload, message: "different" }, id),
    /already used/,
  );
  assert.equal(
    (
      await db.query(
        "SELECT count(*)::int as n FROM records WHERE kind='emergency'",
      )
    ).rows[0].n,
    1,
  );
});
test("cross-organization and cross-site mutations are rejected", async () => {
  const e = await op("worker", "emergency.raise", {
    type: "GAS",
    message: "Test",
  });
  await assert.rejects(
    op("other", "emergency.acknowledge", { emergencyId: e.id }),
    /not found/,
  );
  const remote = await op("remote", "emergency.raise", {
    type: "GAS",
    message: "Other site",
  });
  await assert.rejects(
    op("supervisor", "emergency.acknowledge", { emergencyId: remote.id }),
    /outside your site/,
  );
});
test("assignment is blocked for missing certificate and revocation blocks check-in", async () => {
  const start = new Date(Date.now() + 60000).toISOString(),
    end = new Date(Date.now() + 3600000).toISOString();
  const j = await op("admin", "job.create", {
    title: "Maintenance",
    site: "A",
    start,
    end,
    requirements: ["fire", "gas"],
    ppe: "Approved PPE",
    tasks: ["Inspect detector"],
  });
  await assert.rejects(
    op("admin", "job.assign", { jobId: j.id, workerId: "worker" }),
    /Assignment blocked/,
  );
  const { certificate } = await qualify("gas");
  await op("admin", "job.assign", { jobId: j.id, workerId: "worker" });
  await op("admin", "certificate.revoke", {
    certificateId: certificate.id,
    reason: "Retraining required",
  });
  await assert.rejects(
    op("worker", "attendance.in", { jobId: j.id }),
    /certificates are missing/,
  );
});
test("qualification must remain valid for the entire shift", () => {
  const records = [
    {
      kind: "certificate",
      owner_id: "w",
      data: {
        moduleId: "fire",
        status: "VALID",
        expiresAt: "2030-01-01T10:00:00Z",
      },
    },
  ];
  assert.deepEqual(
    eligibility(
      records,
      "w",
      { requirements: ["fire"], end: "2030-01-01T11:00:00Z" },
      new Date("2030-01-01T09:00:00Z"),
    ),
    ["fire"],
  );
});
test("worker bootstrap does not expose another worker or assessment answer keys", async () => {
  const token = await login("worker");
  const r = await api
    .get("/api/bootstrap")
    .auth(token.accessToken, { type: "bearer" })
    .expect(200);
  assert.ok(r.body.records.every((r) => r.owner_id === "worker"));
  assert.deepEqual(r.body.workers, []);
  assert.ok(
    r.body.modules.every((m) =>
      m.questions.every((q) => !Object.hasOwn(q, "answer")),
    ),
  );
});
test("supervisor cannot view another worker payroll", async () => {
  await op("admin", "payroll.publish", {
    workerId: "worker",
    month: "2026-08",
    base: 2000000,
    overtime: 0,
    incentives: 0,
    deductions: 0,
    status: "PAID",
  });
  const token = await login("supervisor");
  const r = await api
    .get("/api/bootstrap")
    .auth(token.accessToken, { type: "bearer" })
    .expect(200);
  assert.ok(r.body.records.every((r) => r.kind !== "payroll"));
});
test("public certificate verification omits personal and organization data", async () => {
  const id = (
    await db.query("SELECT id FROM records WHERE kind='certificate' LIMIT 1")
  ).rows[0].id;
  const result = await api.get(`/api/certificates/${id}/verify`).expect(200);
  assert.deepEqual(Object.keys(result.body).sort(), [
    "expiresAt",
    "id",
    "issuedAt",
    "moduleId",
    "status",
  ]);
});
test("leave rejects overlaps, excess balance and invalid dates", async () => {
  const year = new Date().getUTCFullYear() + 1;
  await op("worker", "leave.apply", {
    type: "CASUAL",
    start: `${year}-01-01`,
    end: `${year}-01-05`,
    reason: "Family",
  });
  await assert.rejects(
    op("worker", "leave.apply", {
      type: "SICK",
      start: `${year}-01-03`,
      end: `${year}-01-04`,
      reason: "Overlap",
    }),
    /overlapping/,
  );
  await assert.rejects(
    op("worker", "leave.apply", {
      type: "CASUAL",
      start: `${year}-02-01`,
      end: `${year}-02-09`,
      reason: "Excess",
    }),
    /balance/,
  );
  await assert.rejects(
    op("worker", "leave.apply", {
      type: "CASUAL",
      start: `${year}-02-30`,
      end: `${year}-03-01`,
      reason: "Invalid",
    }),
  );
});
test("refresh rotates credentials; replay revokes subsequent sessions", async () => {
  const first = await login("admin");
  const second = (
    await api
      .post("/api/auth/refresh")
      .send({ refreshToken: first.refreshToken })
      .expect(200)
  ).body;
  await api
    .get("/api/bootstrap")
    .auth(first.accessToken, { type: "bearer" })
    .expect(401);
  await api
    .post("/api/auth/refresh")
    .send({ refreshToken: first.refreshToken })
    .expect(401);
  await api
    .get("/api/bootstrap")
    .auth(second.accessToken, { type: "bearer" })
    .expect(401);
});
test("offline attendance remains a claim until an independent review", async () => {
  const start = new Date(Date.now() - 7200000).toISOString(),
    end = new Date(Date.now() - 3600000).toISOString(),
    jobId = randomUUID();
  await db.query(
    "INSERT INTO records(id,org_id,kind,owner_id,data) VALUES($1,$2,$3,$4,$5)",
    [
      jobId,
      "o1",
      "job",
      "worker",
      JSON.stringify({
        title: "Past shift",
        site: "A",
        start,
        end,
        requirements: ["fire"],
        tasks: ["Inspect"],
        completedTasks: [],
        verifiedTasks: [],
      }),
    ],
  );
  const c = await op("worker", "attendance.claim", {
    jobId,
    checkIn: start,
    checkOut: end,
    reason: "No connection at the worksite",
  });
  assert.equal(c.kind, "attendanceClaim");
  assert.equal(c.data.status, "PENDING");
  await assert.rejects(
    op("worker", "attendance.review", {
      claimId: c.id,
      status: "APPROVED",
      reason: "Self approval",
    }),
    /permission/,
  );
  await op("supervisor", "attendance.review", {
    claimId: c.id,
    status: "APPROVED",
    reason: "Verified against site log",
  });
  const records = (
    await db.query(
      "SELECT * FROM records WHERE kind='attendance' AND data->>'jobId'=$1",
      [jobId],
    )
  ).rows;
  assert.equal(records.length, 1);
  assert.equal(records[0].data.minutes, 60);
  assert.equal(records[0].data.source, "REVIEWED_OFFLINE_CLAIM");
});
test("training assignment creates a worker-owned notification", async () => {
  await op("admin", "training.assign", {
    workerId: "worker",
    moduleId: "fire",
    due: `${new Date().getUTCFullYear() + 1}-01-01`,
  });
  const notifications = (
    await db.query(
      "SELECT * FROM records WHERE kind='notification' AND owner_id='worker'",
    )
  ).rows;
  assert.ok(notifications.some((n) => n.data.title === "Training assigned"));
  await assert.rejects(
    op("remote", "notification.read", { notificationId: notifications[0].id }),
    /another worker/,
  );
});

test("qualified worker can check in, submit tasks, receive verification and check out", async () => {
  await db.query(
    "INSERT INTO users(id,org_id,employee_id,name,email,role,site) VALUES('lifecycle','o1','lifecycle','Lifecycle worker','+919000000099','WORKER','A')",
  );
  actors.lifecycle = (
    await db.query("SELECT * FROM users WHERE id='lifecycle'")
  ).rows[0];
  await qualify("fire", "lifecycle");
  const start = new Date(Date.now() + 3600000).toISOString(),
    end = new Date(Date.now() + 7200000).toISOString();
  const j = await op("admin", "job.create", {
    title: "Inspection shift",
    site: "A",
    start,
    end,
    requirements: ["fire"],
    ppe: "Site-approved PPE",
    tasks: ["Inspect ventilation"],
  });
  await op("admin", "job.assign", { jobId: j.id, workerId: "lifecycle" });
  await assert.rejects(
    op("lifecycle", "task.complete", {
      jobId: j.id,
      index: 0,
      note: "Inspection complete",
    }),
    /Check in/,
  );
  // Advance only the fixture's shift window, without sleeping or altering the machine clock.
  await db.query(
    "UPDATE records SET data=jsonb_set(data,'{start}',to_jsonb($1::text)) WHERE id=$2",
    [new Date(Date.now() - 60000).toISOString(), j.id],
  );
  const attendance = await op("lifecycle", "attendance.in", { jobId: j.id });
  await assert.rejects(
    op("lifecycle", "attendance.in", { jobId: j.id }),
    /Already checked in/,
  );
  const task = await op("lifecycle", "task.complete", {
    jobId: j.id,
    index: 0,
    note: "Inspection complete",
  });
  assert.deepEqual(task.data.completedTasks, [0]);
  const reviewed = await op("supervisor", "task.verify", {
    jobId: j.id,
    index: 0,
  });
  assert.deepEqual(reviewed.data.verifiedTasks, [0]);
  const checkout = await op("lifecycle", "attendance.out", {
    attendanceId: attendance.id,
  });
  assert.ok(checkout.data.checkOut);
  assert.ok(checkout.data.minutes >= 0);
  await assert.rejects(
    op("lifecycle", "attendance.out", { attendanceId: attendance.id }),
    /Already checked out/,
  );
});

test("email login sends only to the matched employee and manager portal rejects workers", async () => {
  for (const [who, email, portal, allowed] of [
    ["worker", actors.worker.email, "worker", true],
    ["admin", actors.worker.email, "manager", false],
    ["worker", actors.worker.email, "manager", false],
    ["admin", actors.admin.email, "manager", true],
  ]) {
    await db.query("DELETE FROM auth_throttles");
    const response = await api
      .post("/api/auth/request")
      .send({ organization: "o1", employeeId: who, email, portal })
      .expect(200);
    const id = response.body.challengeId;
    assert.equal(destinations.has(id), allowed);
    if (allowed) assert.equal(destinations.get(id), email);
    else {
      const row = (
        await db.query("SELECT user_id FROM otp_challenges WHERE id=$1", [id])
      ).rows[0];
      assert.equal(row.user_id, null);
      await api
        .post("/api/auth/verify")
        .send({ challengeId: id, code: "123456" })
        .expect(401);
    }
  }
});

test("payroll can be published and corrected by admin, but not by a worker", async () => {
  const payload = {
    workerId: "worker",
    month: "2026-09",
    base: 2000000,
    overtime: 50000,
    incentives: 10000,
    deductions: 20000,
    status: "PENDING",
  };
  const first = await op("admin", "payroll.publish", payload);
  assert.equal(first.data.net, 2040000);
  const updated = await op("admin", "payroll.publish", {
    ...payload,
    base: 2100000,
    status: "PAID",
  });
  assert.equal(updated.id, first.id);
  assert.equal(updated.data.net, 2140000);
  await assert.rejects(op("worker", "payroll.publish", payload));
});

test("email OTP rejects legacy phone payload and never exposes a debug code", async () => {
  await db.query("DELETE FROM auth_throttles");
  await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "admin",
      phone: "attacker@example.test",
    })
    .expect(400);
  const response = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "admin",
      email: " ADMIN@EXAMPLE.TEST ",
    })
    .expect(200);
  assert.equal(response.body.debugCode, undefined);
  assert.equal(
    destinations.get(response.body.challengeId),
    "admin@example.test",
  );
});

test("self registration stores email, requires approval and cannot assign a role", async () => {
  const input = {
    organization: "o1",
    employeeId: "new-email-worker",
    name: "New Worker",
    email: " NEW@EXAMPLE.TEST ",
    site: "A",
  };
  await api
    .post("/api/workers/self-register")
    .send({ ...input, role: "ORG_ADMIN" })
    .expect(400);
  await api.post("/api/workers/self-register").send(input).expect(200);
  const row = (
    await db.query("SELECT * FROM users WHERE employee_id=$1", [
      input.employeeId,
    ])
  ).rows[0];
  assert.equal(row.email, "new@example.test");
  assert.equal(row.active, false);
  assert.equal(row.role, "WORKER");
  await db.query("DELETE FROM auth_throttles");
  const before = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: input.employeeId,
      email: input.email,
    })
    .expect(200);
  assert.equal(destinations.has(before.body.challengeId), false);
  const admin = await login("admin");
  await api
    .post(`/api/admin/workers/${row.id}/approve`)
    .set("Authorization", `Bearer ${admin.accessToken}`)
    .send({})
    .expect(200);
  await db.query("DELETE FROM auth_throttles");
  const after = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: input.employeeId,
      email: input.email,
    })
    .expect(200);
  assert.equal(destinations.get(after.body.challengeId), "new@example.test");
});

test("admin email migration is tenant-scoped and revokes the worker's codes and sessions", async () => {
  const admin = await login("admin"),
    worker = await login("worker"),
    other = await login("other");
  const bearer = (token) => `Bearer ${token.accessToken}`;
  await api
    .post("/api/workers/worker/email")
    .set("Authorization", bearer(worker))
    .send({ email: "changed@example.test" })
    .expect(403);
  await api
    .post("/api/workers/worker/email")
    .set("Authorization", bearer(other))
    .send({ email: "changed@example.test" })
    .expect(404);
  await db.query("DELETE FROM auth_throttles");
  const pending = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "worker",
      email: actors.worker.email,
    })
    .expect(200);
  await api
    .post("/api/workers/worker/email")
    .set("Authorization", bearer(admin))
    .send({ email: "changed@example.test" })
    .expect(200);
  await api
    .post("/api/auth/verify")
    .send({
      challengeId: pending.body.challengeId,
      code: codes.get(pending.body.challengeId),
    })
    .expect(401);
  await api
    .get("/api/bootstrap")
    .set("Authorization", bearer(worker))
    .expect(401);
});

test("a failed SMTP send consumes the generated challenge", async () => {
  await db.query("DELETE FROM auth_throttles");
  const failing = supertest(
    createApp(db, {
      jwtSecret: "a".repeat(40),
      otpPepper: "b".repeat(40),
      publicUrl: "https://example.test",
      sendOtp: async () => {
        const e = Error("Mail unavailable");
        e.status = 503;
        throw e;
      },
    }),
  );
  await failing
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: "admin",
      email: actors.admin.email,
    })
    .expect(503);
  const active = (
    await db.query(
      "SELECT count(*)::int AS count FROM otp_challenges WHERE user_id='admin' AND consumed=false",
    )
  ).rows[0].count;
  assert.equal(active, 0);
});

test("created employee persists, appears in the admin directory and can request email OTP", async () => {
  const admin = await login("admin");
  const auth = { Authorization: `Bearer ${admin.accessToken}` };
  const payload = {
    employeeId: "persisted-worker",
    name: "Directory test",
    email: "directory@example.test",
    role: "WORKER",
    site: "A",
  };
  const created = await api
    .post("/api/workers")
    .set(auth)
    .send(payload)
    .expect(201);
  const stored = (
    await db.query("SELECT * FROM users WHERE id=$1", [created.body.id])
  ).rows[0];
  assert.equal(stored.employee_id, payload.employeeId);
  assert.equal(stored.email, payload.email);
  assert.equal(stored.active, true);
  const directory = await api.get("/api/bootstrap").set(auth).expect(200);
  const visible = directory.body.workers.find((w) => w.id === created.body.id);
  assert.equal(visible.employeeId, payload.employeeId);
  assert.equal(visible.email, payload.email);
  const otp = await api
    .post("/api/auth/request")
    .send({
      organization: "o1",
      employeeId: payload.employeeId,
      email: payload.email,
    })
    .expect(200);
  assert.equal(destinations.get(otp.body.challengeId), payload.email);
  await api.post("/api/workers").set(auth).send(payload).expect(409);
});
