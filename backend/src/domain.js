import { randomUUID, createHash } from "node:crypto";
import { z } from "zod";
import { modules } from "./catalog.js";
export class Fault extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export const need = (condition, message, status = 409) => {
  if (!condition) throw new Fault(status, message);
};
const id = z.string().min(1).max(100);
const date = z.iso.date();
const text = z.string().trim().min(1).max(500);
const schemas = {
  "training.complete": z.object({
    moduleId: z.enum(["fire", "gas"]),
    version: z.literal(1),
    steps: z.array(z.string()).max(20),
    durationSeconds: z.number().int().min(30).max(7200),
    mode: z.enum(["AR", "PRACTICE"]),
  }),
  "assessment.submit": z.object({
    moduleId: z.enum(["fire", "gas"]),
    answers: z.array(z.number().int().min(0).max(2)).length(5),
  }),
  "attempt.approve": z.object({ attemptId: id }),
  "certificate.revoke": z.object({ certificateId: id, reason: text }),
  "job.create": z.object({
    title: text,
    site: text,
    start: z.iso.datetime(),
    end: z.iso.datetime(),
    requirements: z
      .array(z.enum(["fire", "gas"]))
      .min(1)
      .max(2),
    ppe: text,
    tasks: z.array(text).min(1).max(20),
  }),
  "job.assign": z.object({ jobId: id, workerId: id }),
  "attendance.in": z.object({ jobId: id }),
  "attendance.out": z.object({ attendanceId: id }),
  "attendance.claim": z.object({
    jobId: id,
    checkIn: z.iso.datetime(),
    checkOut: z.iso.datetime(),
    reason: text,
  }),
  "attendance.review": z.object({
    claimId: id,
    status: z.enum(["APPROVED", "REJECTED"]),
    reason: text,
  }),
  "task.complete": z.object({
    jobId: id,
    index: z.number().int().min(0).max(19),
    note: text,
  }),
  "task.verify": z.object({
    jobId: id,
    index: z.number().int().min(0).max(19),
  }),
  "leave.apply": z.object({
    type: z.enum(["CASUAL", "SICK"]),
    start: date,
    end: date,
    reason: text,
  }),
  "leave.decide": z.object({
    leaveId: id,
    status: z.enum(["APPROVED", "REJECTED"]),
    reason: text,
  }),
  "payroll.publish": z.object({
    workerId: id,
    month: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
    base: z.number().int().min(0).max(100000000),
    overtime: z.number().int().min(0).max(100000000),
    incentives: z.number().int().min(0).max(100000000),
    deductions: z.number().int().min(0).max(100000000),
    status: z.enum(["PENDING", "PAID"]),
  }),
  "emergency.raise": z.object({
    type: z.enum(["FIRE", "GAS", "INJURY", "MACHINERY", "OTHER"]),
    message: z.string().max(500),
    latitude: z.number().min(-90).max(90).optional(),
    longitude: z.number().min(-180).max(180).optional(),
  }),
  "emergency.acknowledge": z.object({ emergencyId: id }),
  "training.assign": z.object({
    workerId: id,
    moduleId: z.enum(["fire", "gas"]),
    due: date,
  }),
  "notification.read": z.object({ notificationId: id }),
};
const rights = {
  "attempt.approve": ["TRAINER", "SAFETY_OFFICER", "ORG_ADMIN"],
  "certificate.revoke": ["SAFETY_OFFICER", "ORG_ADMIN"],
  "job.create": ["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"],
  "job.assign": ["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"],
  "task.verify": ["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"],
  "leave.decide": ["SUPERVISOR", "HR", "ORG_ADMIN"],
  "payroll.publish": ["HR", "ORG_ADMIN"],
  "emergency.acknowledge": [
    "SUPERVISOR",
    "SAFETY_OFFICER",
    "SITE_ADMIN",
    "ORG_ADMIN",
  ],
  "training.assign": ["TRAINER", "SAFETY_OFFICER", "SUPERVISOR", "ORG_ADMIN"],
  "attendance.review": ["SUPERVISOR", "HR", "ORG_ADMIN"],
};
export function eligibility(records, workerId, job, at = new Date()) {
  const until = new Date(Math.max(+new Date(job.end), +at));
  return job.requirements.filter(
    (moduleId) =>
      !records.some(
        (r) =>
          r.kind === "certificate" &&
          r.owner_id === workerId &&
          r.data.moduleId === moduleId &&
          r.data.status === "VALID" &&
          new Date(r.data.expiresAt) > until,
      ),
  );
}
export async function applyOperation(db, user, raw) {
  const op = z
    .object({
      id: z.uuid(),
      type: z.enum(Object.keys(schemas)),
      payload: z.unknown(),
    })
    .parse(raw);
  const p = schemas[op.type].strict().parse(op.payload);
  if (rights[op.type])
    need(
      rights[op.type].includes(user.role),
      "You do not have permission for this action.",
      403,
    );
  const digest = createHash("sha256")
    .update(JSON.stringify({ type: op.type, payload: p }))
    .digest("hex");
  return db.transaction(async (tx) => {
    // Organization lock serializes eligibility, revocation, assignment and leave decisions.
    await tx.query("SELECT id FROM organizations WHERE id=$1 FOR UPDATE", [
      user.org_id,
    ]);
    const previous = (
      await tx.query("SELECT * FROM operations WHERE user_id=$1 AND id=$2", [
        user.id,
        op.id,
      ])
    ).rows[0];
    if (previous) {
      need(
        previous.digest === digest,
        "This operation ID was already used for another action.",
      );
      return previous.result;
    }
    const records = (
      await tx.query("SELECT * FROM records WHERE org_id=$1", [user.org_id])
    ).rows;
    const get = (rid, kind) => {
      const r = records.find((x) => x.id === rid && x.kind === kind);
      need(r, "Record not found.", 404);
      return r;
    };
    const own = (r) =>
      need(
        r.owner_id === user.id,
        "This record belongs to another worker.",
        403,
      );
    const scoped = (r) =>
      need(
        ["ORG_ADMIN", "HR"].includes(user.role) || r.data.site === user.site,
        "This record is outside your site.",
        403,
      );
    const worker = async (wid) => {
      const w = (
        await tx.query(
          "SELECT * FROM users WHERE id=$1 AND org_id=$2 AND active=true",
          [wid, user.org_id],
        )
      ).rows[0];
      need(w, "Worker not found.", 404);
      need(
        user.role === "ORG_ADMIN" || user.role === "HR" || w.site === user.site,
        "Worker is outside your site.",
        403,
      );
      return w;
    };
    const now = new Date(),
      iso = now.toISOString();
    const save = async (kind, owner, data, rid = randomUUID()) => {
      await tx.query(
        "INSERT INTO records(id,org_id,kind,owner_id,data) VALUES($1,$2,$3,$4,$5::jsonb) ON CONFLICT(id) DO UPDATE SET data=EXCLUDED.data,owner_id=EXCLUDED.owner_id,version=records.version+1,updated_at=now()",
        [rid, user.org_id, kind, owner, JSON.stringify(data)],
      );
      if (kind === "notification")
        await tx.query(
          "INSERT INTO push_jobs(notification_id) VALUES($1) ON CONFLICT DO NOTHING",
          [rid],
        );
      return { id: rid, kind, owner_id: owner, data };
    };
    let result;
    switch (op.type) {
      case "training.complete": {
        const m = modules.find((m) => m.id === p.moduleId);
        need(
          p.steps.join("|") === m.steps.map((s) => s.target).join("|"),
          "Complete every training step in sequence.",
        );
        result = await save("progress", user.id, {
          ...p,
          completedAt: iso,
          site: user.site,
        });
        break;
      }
      case "assessment.submit": {
        const training = records
          .filter(
            (r) =>
              r.kind === "progress" &&
              r.owner_id === user.id &&
              r.data.moduleId === p.moduleId &&
              r.data.version === 1,
          )
          .sort((a, b) =>
            b.data.completedAt.localeCompare(a.data.completedAt),
          )[0];
        need(training, "Complete the training before taking the assessment.");
        const attempts = records.filter(
          (r) =>
            r.kind === "attempt" &&
            r.owner_id === user.id &&
            r.data.moduleId === p.moduleId,
        );
        need(
          attempts.filter(
            (r) => r.data.completedAt.slice(0, 10) === iso.slice(0, 10),
          ).length < 5,
          "Daily attempt limit reached. Review training and try tomorrow.",
        );
        const m = modules.find((m) => m.id === p.moduleId),
          weak = m.questions
            .filter((q, i) => q.answer !== p.answers[i])
            .map((q) => q.topic);
        const score = (5 - weak.length) * 20;
        result = await save("attempt", user.id, {
          moduleId: p.moduleId,
          score,
          weakTopics: weak,
          passed: score >= 80,
          completedAt: iso,
          attemptNumber: attempts.length + 1,
          practicalMode: training.data.mode,
          status: score >= 80 ? "AWAITING_PRACTICAL_REVIEW" : "FAILED",
          site: user.site,
        });
        break;
      }
      case "attempt.approve": {
        const a = get(p.attemptId, "attempt");
        scoped(a);
        need(
          a.owner_id !== user.id,
          "A reviewer cannot approve their own assessment.",
          403,
        );
        need(
          a.data.passed && a.data.status === "AWAITING_PRACTICAL_REVIEW",
          "This assessment is not awaiting approval.",
        );
        await save(
          "attempt",
          a.owner_id,
          { ...a.data, status: "APPROVED", reviewer: user.id },
          a.id,
        );
        result = await save("certificate", a.owner_id, {
          moduleId: a.data.moduleId,
          issuedAt: iso,
          expiresAt: new Date(+now + 365 * 86400000).toISOString(),
          status: "VALID",
          attemptId: a.id,
          reviewer: user.id,
          site: a.data.site,
        });
        break;
      }
      case "certificate.revoke": {
        const c = get(p.certificateId, "certificate");
        scoped(c);
        result = await save(
          "certificate",
          c.owner_id,
          { ...c.data, status: "REVOKED", reason: p.reason },
          c.id,
        );
        break;
      }
      case "job.create": {
        need(
          new Date(p.end) > new Date(p.start) && new Date(p.start) > now,
          "Choose a future shift with an end after its start.",
        );
        need(
          user.role === "ORG_ADMIN" || p.site === user.site,
          "Cannot create jobs outside your site.",
          403,
        );
        result = await save("job", null, {
          ...p,
          status: "UNASSIGNED",
          supervisor: user.name,
          completedTasks: [],
          verifiedTasks: [],
        });
        break;
      }
      case "job.assign": {
        const j = get(p.jobId, "job");
        scoped(j);
        const w = await worker(p.workerId);
        need(
          w.site === j.data.site,
          "Worker and job must belong to the same site.",
        );
        need(!j.owner_id, "Job is already assigned.");
        need(new Date(j.data.end) > now, "This shift has ended.");
        const missing = eligibility(records, w.id, j.data, now);
        need(
          !missing.length,
          `Assignment blocked. Required valid certificates: ${missing.join(", ")}.`,
        );
        need(
          !records.some(
            (r) =>
              r.kind === "job" &&
              r.owner_id === w.id &&
              new Date(r.data.start) < new Date(j.data.end) &&
              new Date(r.data.end) > new Date(j.data.start),
          ),
          "Worker has an overlapping shift.",
        );
        need(
          !records.some(
            (r) =>
              r.kind === "leave" &&
              r.owner_id === w.id &&
              r.data.status === "APPROVED" &&
              r.data.start <= j.data.end.slice(0, 10) &&
              r.data.end >= j.data.start.slice(0, 10),
          ),
          "Worker is on approved leave.",
        );
        result = await save(
          "job",
          w.id,
          { ...j.data, status: "ASSIGNED" },
          j.id,
        );
        break;
      }
      case "attendance.in": {
        const j = get(p.jobId, "job");
        own(j);
        need(
          !eligibility(records, user.id, j.data, now).length,
          "Check-in blocked: required certificates are missing, expired or revoked.",
        );
        need(
          now >= new Date(j.data.start) && now < new Date(j.data.end),
          "Check in during your assigned shift.",
        );
        need(
          !records.some(
            (r) =>
              r.kind === "attendance" &&
              r.owner_id === user.id &&
              (!r.data.checkOut || r.data.jobId === j.id),
          ),
          "Already checked in or this shift has been recorded.",
        );
        need(
          !records.some(
            (r) =>
              r.kind === "leave" &&
              r.owner_id === user.id &&
              r.data.status === "APPROVED" &&
              r.data.start <= iso.slice(0, 10) &&
              r.data.end >= iso.slice(0, 10),
          ),
          "Check-in blocked by approved leave.",
        );
        result = await save("attendance", user.id, {
          jobId: j.id,
          checkIn: iso,
          checkOut: null,
          site: user.site,
        });
        break;
      }
      case "attendance.out": {
        const a = get(p.attendanceId, "attendance");
        own(a);
        need(!a.data.checkOut, "Already checked out.");
        result = await save(
          "attendance",
          user.id,
          {
            ...a.data,
            checkOut: iso,
            minutes: Math.floor((+now - new Date(a.data.checkIn)) / 60000),
          },
          a.id,
        );
        break;
      }
      case "attendance.claim": {
        const j = get(p.jobId, "job");
        own(j);
        need(
          new Date(p.checkOut) > new Date(p.checkIn) &&
            new Date(p.checkOut) <= now,
          "Claim times must be in the past, with check-out after check-in.",
        );
        need(
          new Date(p.checkIn) >= new Date(j.data.start) &&
            new Date(p.checkOut) <= new Date(j.data.end),
          "Claim times must fall within the assigned shift.",
        );
        need(
          !records.some(
            (r) =>
              r.owner_id === user.id &&
              ["attendance", "attendanceClaim"].includes(r.kind) &&
              r.data.jobId === j.id &&
              r.data.status !== "REJECTED",
          ),
          "An attendance record or pending claim already exists for this shift.",
        );
        result = await save("attendanceClaim", user.id, {
          ...p,
          status: "PENDING",
          site: user.site,
          receivedAt: iso,
        });
        break;
      }
      case "attendance.review": {
        const c = get(p.claimId, "attendanceClaim");
        scoped(c);
        need(c.owner_id !== user.id, "Cannot review your own attendance.", 403);
        need(c.data.status === "PENDING", "Claim has already been reviewed.");
        if (p.status === "APPROVED") {
          need(
            !records.some(
              (r) =>
                r.kind === "attendance" &&
                r.owner_id === c.owner_id &&
                r.data.jobId === c.data.jobId,
            ),
            "Attendance already recorded for this shift.",
          );
          await save("attendance", c.owner_id, {
            jobId: c.data.jobId,
            checkIn: c.data.checkIn,
            checkOut: c.data.checkOut,
            minutes: Math.floor(
              (new Date(c.data.checkOut) - new Date(c.data.checkIn)) / 60000,
            ),
            source: "REVIEWED_OFFLINE_CLAIM",
            reviewer: user.id,
            site: c.data.site,
          });
        }
        result = await save(
          "attendanceClaim",
          c.owner_id,
          {
            ...c.data,
            status: p.status,
            decisionReason: p.reason,
            reviewer: user.id,
          },
          c.id,
        );
        break;
      }
      case "task.complete":
      case "task.verify": {
        const j = get(p.jobId, "job");
        need(p.index < j.data.tasks.length, "Task does not exist.", 404);
        if (op.type === "task.complete") {
          own(j);
          need(
            !eligibility(records, user.id, j.data, now).length,
            "Required safety certification is no longer valid.",
          );
          need(
            records.some(
              (r) =>
                r.kind === "attendance" &&
                r.owner_id === user.id &&
                r.data.jobId === j.id &&
                !r.data.checkOut,
            ),
            "Check in before submitting tasks.",
          );
        } else {
          scoped(j);
          need(
            j.data.completedTasks.includes(p.index),
            "Worker must submit this task first.",
          );
        }
        const key =
          op.type === "task.complete" ? "completedTasks" : "verifiedTasks";
        result = await save(
          "job",
          j.owner_id,
          {
            ...j.data,
            [key]: [...new Set([...j.data[key], p.index])],
            notes: {
              ...j.data.notes,
              ...(p.note ? { [p.index]: p.note } : {}),
            },
          },
          j.id,
        );
        break;
      }
      case "leave.apply": {
        need(
          p.start >= iso.slice(0, 10) && p.end >= p.start,
          "Select valid current or future dates.",
        );
        const days =
          Math.floor((new Date(p.end) - new Date(p.start)) / 86400000) + 1;
        need(days <= 30, "Apply for at most 30 days at once.");
        need(
          p.start.slice(0, 4) === p.end.slice(0, 4),
          "Submit separate requests for different years.",
        );
        need(
          !records.some(
            (r) =>
              r.kind === "leave" &&
              r.owner_id === user.id &&
              r.data.status !== "REJECTED" &&
              r.data.start <= p.end &&
              r.data.end >= p.start,
          ),
          "An overlapping leave request exists.",
        );
        const used = records
          .filter(
            (r) =>
              r.kind === "leave" &&
              r.owner_id === user.id &&
              r.data.type === p.type &&
              r.data.status !== "REJECTED" &&
              r.data.start.slice(0, 4) === p.start.slice(0, 4),
          )
          .reduce((s, r) => s + r.data.days, 0);
        need(
          used + days <= 12,
          "Insufficient leave balance (12 days per type per year).",
        );
        result = await save("leave", user.id, {
          ...p,
          days,
          status: "PENDING",
          site: user.site,
        });
        break;
      }
      case "leave.decide": {
        const l = get(p.leaveId, "leave");
        scoped(l);
        need(l.owner_id !== user.id, "Cannot approve your own leave.", 403);
        need(l.data.status === "PENDING", "Request has already been decided.");
        if (p.status === "APPROVED")
          need(
            !records.some(
              (r) =>
                r.kind === "job" &&
                r.owner_id === l.owner_id &&
                r.data.start.slice(0, 10) <= l.data.end &&
                r.data.end.slice(0, 10) >= l.data.start,
            ),
            "Resolve assigned shifts before approving leave.",
          );
        result = await save(
          "leave",
          l.owner_id,
          {
            ...l.data,
            status: p.status,
            decisionReason: p.reason,
            reviewer: user.id,
          },
          l.id,
        );
        break;
      }
      case "payroll.publish": {
        const w = await worker(p.workerId);
        const net = p.base + p.overtime + p.incentives - p.deductions;
        need(net >= 0, "Deductions exceed earnings.");
        const existing = records.find(
          (r) =>
            r.kind === "payroll" &&
            r.owner_id === w.id &&
            r.data.month === p.month,
        );
        result = await save(
          "payroll",
          w.id,
          { ...p, net, currency: "INR", site: w.site },
          existing?.id,
        );
        break;
      }
      case "emergency.raise":
        result = await save("emergency", user.id, {
          ...p,
          site: user.site,
          createdAt: iso,
          status: "RECEIVED",
        });
        break;
      case "emergency.acknowledge": {
        const e = get(p.emergencyId, "emergency");
        scoped(e);
        result = await save(
          "emergency",
          e.owner_id,
          {
            ...e.data,
            status: "ACKNOWLEDGED",
            acknowledgedBy: user.id,
            acknowledgedAt: iso,
          },
          e.id,
        );
        break;
      }
      case "training.assign": {
        const w = await worker(p.workerId);
        need(p.due >= iso.slice(0, 10), "Due date cannot be in the past.");
        result = await save("trainingAssignment", w.id, { ...p, site: w.site });
        break;
      }
      case "notification.read": {
        const n = get(p.notificationId, "notification");
        own(n);
        result = await save(
          "notification",
          user.id,
          { ...n.data, read: true },
          n.id,
        );
        break;
      }
    }
    if (op.type !== "notification.read") {
      const titles = {
        "training.assign": "Training assigned",
        "job.assign": "New job assigned",
        "attempt.approve": "Certificate issued",
        "certificate.revoke": "Certificate revoked",
        "leave.decide": "Leave request updated",
        "payroll.publish": "Payslip available",
        "emergency.acknowledge": "Emergency response acknowledged",
        "attendance.review": "Offline attendance reviewed",
      };
      if (titles[op.type] && result.owner_id)
        await save("notification", result.owner_id, {
          title: titles[op.type],
          message: "Open the relevant record to review the update.",
          recordId: result.id,
          createdAt: iso,
          read: false,
          site: result.data.site,
        });
      if (op.type === "emergency.raise") {
        const responders = (
          await tx.query(
            "SELECT id FROM users WHERE org_id=$1 AND active=true AND ((site=$2 AND role IN ('SUPERVISOR','SAFETY_OFFICER','SITE_ADMIN')) OR role='ORG_ADMIN')",
            [user.org_id, user.site],
          )
        ).rows;
        for (const responder of responders)
          await save("notification", responder.id, {
            title: "Emergency alert received",
            message: `${p.type} at ${user.site}. Open Emergencies and follow the site response plan.`,
            recordId: result.id,
            createdAt: iso,
            read: false,
            site: user.site,
          });
      }
    }
    await tx.query(
      "INSERT INTO operations(user_id,id,digest,result) VALUES($1,$2,$3,$4::jsonb)",
      [user.id, op.id, digest, JSON.stringify(result)],
    );
    await tx.query(
      "INSERT INTO audit_log(id,org_id,actor_id,action,record_id) VALUES($1,$2,$3,$4,$5)",
      [randomUUID(), user.org_id, user.id, op.type, result.id],
    );
    return result;
  });
}
