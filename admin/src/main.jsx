import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";

// Tokens intentionally remain in memory; no localStorage/sessionStorage credentials.
let session = null;
let refreshPending = null;
const pendingOperations = new Map();
async function request(path, body, retry = true) {
  const sentAccess = session?.accessToken;
  const r = await fetch(`/api/${path}`, {
    method: body ? "POST" : "GET",
    headers: {
      "Content-Type": "application/json",
      ...(sentAccess ? { Authorization: `Bearer ${sentAccess}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (r.status === 401 && session && retry) {
    if (session.accessToken !== sentAccess) return request(path, body, false);
    if (!refreshPending)
      refreshPending = (async () => {
        const refresh = await fetch("/api/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        });
        if (!refresh.ok) {
          session = null;
          throw Error("Session expired. Sign in again.");
        }
        session = await refresh.json();
      })().finally(() => {
        refreshPending = null;
      });
    await refreshPending;
    return request(path, body, false);
  }
  const result = await r.json();
  if (!r.ok) throw Error(result.error ?? "Unable to complete this request.");
  return result;
}
const friendly = (value) => String(value ?? "").replaceAll("_", " ");
const time = (value) => (value ? new Date(value).toLocaleString() : "—");
const money = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    value / 100,
  );
function Field({
  label,
  name,
  type = "text",
  required = true,
  options,
  ...props
}) {
  return (
    <label>
      {label}
      {options ? (
        <select name={name} required={required} {...props}>
          {options.map((o) => (
            <option
              key={typeof o === "string" ? o : o.value}
              value={typeof o === "string" ? o : o.value}
            >
              {typeof o === "string" ? friendly(o) : o.label}
            </option>
          ))}
        </select>
      ) : (
        <input name={name} type={type} required={required} {...props} />
      )}
    </label>
  );
}
function Form({ title, children, submit, label = "Save", busy }) {
  return (
    <section className="panel">
      <h2>{title}</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(Object.fromEntries(new FormData(e.currentTarget)));
        }}
      >
        {children}
        <button disabled={busy}>{busy ? "Saving…" : label}</button>
      </form>
    </section>
  );
}
function Badge({ children }) {
  return (
    <span
      className={`badge ${["VALID", "PAID", "APPROVED", "ACKNOWLEDGED"].includes(children) ? "good" : ["REVOKED", "EXPIRED", "FAILED", "BLOCKED"].includes(children) ? "danger" : ""}`}
    >
      {friendly(children)}
    </span>
  );
}
function Empty({ children }) {
  return <p className="empty">{children}</p>;
}
function App() {
  const [data, setData] = useState(null),
    [page, setPage] = useState("Overview"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [challenge, setChallenge] = useState(""),
    [notice, setNotice] = useState(""),
    [audit, setAudit] = useState([]);
  const [verification, setVerification] = useState(null);
  const verifyId = location.pathname.startsWith("/verify/")
    ? location.pathname.split("/").pop()
    : null;
  async function run(fn) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e.message);
      if (!session) setData(null);
    } finally {
      setBusy(false);
    }
  }
  async function reload() {
    const d = await request("bootstrap");
    setData(d);
  }
  async function operation(type, payload) {
    await run(async () => {
      const key = JSON.stringify({ type, payload });
      const id = pendingOperations.get(key) ?? crypto.randomUUID();
      pendingOperations.set(key, id);
      await request("operations", { id, type, payload });
      pendingOperations.delete(key);
      await reload();
      setNotice("Changes saved.");
    });
  }
  useEffect(() => {
    if (verifyId)
      request(`certificates/${encodeURIComponent(verifyId)}/verify`)
        .then(setVerification)
        .catch(() => setVerification({ status: "INVALID" }));
  }, [verifyId]);
  useEffect(() => {
    if (!data) return;
    const timer = setInterval(() => {
      request("bootstrap")
        .then(setData)
        .catch((e) => setError(e.message));
    }, 30000);
    return () => clearInterval(timer);
  }, [!!data]);
  if (verifyId)
    return (
      <main className="verification">
        <div className="brand">SurakshaSetu</div>
        <h1>Certificate verification</h1>
        {verification ? (
          <section className="panel">
            <Badge>{verification.status}</Badge>
            {verification.moduleId && (
              <>
                <h2>
                  {verification.moduleId === "fire"
                    ? "Fire & Explosion Response"
                    : "Gas Leak & Confined Space"}
                </h2>
                <p>Issued {time(verification.issuedAt)}</p>
                <p>Expires {time(verification.expiresAt)}</p>
                <small>{verification.id}</small>
              </>
            )}
            <p>
              Confirm the worker's identity separately. This record is an
              internal training qualification.
            </p>
          </section>
        ) : (
          <p>Checking current status…</p>
        )}
      </main>
    );
  if (!data)
    return (
      <main className="login">
        <div className="brand">SurakshaSetu</div>
        <h1>Manager login</h1>
        <p>Training, qualifications and daily work in one place.</p>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        {!challenge ? (
          <Form
            title="Sign in"
            busy={busy}
            label="Send verification code"
            submit={(p) =>
              run(async () => {
                const r = await request("auth/request", {
                  ...p,
                  portal: "manager",
                });
                setChallenge(r.challengeId);
              })
            }
          >
            <Field
              label="Organization ID"
              name="organization"
              autoComplete="organization"
            />
            <Field
              label="Employee ID"
              name="employeeId"
              autoComplete="username"
            />
            <Field
              label="Registered mobile number (+91…)"
              name="phone"
              type="tel"
              autoComplete="tel"
              pattern="\+[1-9][0-9]{7,14}"
            />
            <p>
              Use the phone registered for your staff account. Workers sign in
              through the mobile app.
            </p>
          </Form>
        ) : (
          <Form
            title="Verify your account"
            busy={busy}
            label="Verify and sign in"
            submit={(p) =>
              run(async () => {
                session = await request("auth/verify", {
                  challengeId: challenge,
                  code: p.code,
                });
                if (session.user.role === "WORKER") {
                  await request("auth/logout", {});
                  session = null;
                  throw Error(
                    "This console is for managers and staff. Use the worker mobile app.",
                  );
                }
                await reload();
              })
            }
          >
            <p>
              If the staff account and phone match, a code was sent to that
              number.
            </p>
            <Field
              label="Verification code"
              name="code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              autoComplete="one-time-code"
            />
            <button
              type="button"
              className="secondary"
              onClick={() => setChallenge("")}
            >
              Request another code
            </button>
          </Form>
        )}
      </main>
    );
  const records = data.records,
    workers = data.workers;
  const rows = (kind) => records.filter((r) => r.kind === kind);
  const workerName = (id) =>
    workers.find((w) => w.id === id)?.name ??
    (id === data.user.id ? data.user.name : "Unassigned");
  const moduleName = (id) => data.modules.find((m) => m.id === id)?.title ?? id;
  const options = workers
    .filter((w) => w.role === "WORKER")
    .map((w) => ({ value: w.id, label: `${w.name} • ${w.employeeId}` }));
  const can = (roles) => roles.includes(data.user.role);
  const admin = can(["ORG_ADMIN"]);
  const sections = [
    "Overview",
    "Workers",
    "Pending Approvals",
    "Training",
    "Assessments",
    "Certificates",
    "Jobs",
    "Tasks",
    "Attendance",
    "Leave",
    ...(can(["HR", "ORG_ADMIN"]) ? ["Payroll"] : []),
    "Emergencies",
    ...(admin ? ["Audit"] : []),
  ];
  return (
    <div className="shell">
      <aside>
        <div className="brand">SurakshaSetu</div>
        <p className="subbrand">Workforce safety</p>
        <nav>
          {sections.map((s) => (
            <button
              key={s}
              className={page === s ? "active" : ""}
              onClick={() => {
                setPage(s);
                setNotice("");
                if (s === "Audit")
                  run(async () => setAudit(await request("audit")));
              }}
            >
              {s}
              {s === "Emergencies" &&
              rows("emergency").some((e) => e.data.status === "RECEIVED")
                ? " •"
                : ""}
            </button>
          ))}
        </nav>
        <div className="account">
          <strong>{data.user.name}</strong>
          <small>
            {friendly(data.user.role)}
            <br />
            {data.user.site}
          </small>
          <button
            className="secondary"
            onClick={() =>
              run(async () => {
                await request("auth/logout", {});
                session = null;
                setData(null);
                setChallenge("");
              })
            }
          >
            Sign out
          </button>
        </div>
      </aside>
      <main>
        <header>
          <div>
            <p className="eyebrow">{data.user.site} / SAFETY OPERATIONS</p>
            <h1>{page}</h1>
          </div>
          <button
            className="secondary"
            disabled={busy}
            onClick={() => run(reload)}
          >
            Refresh
          </button>
        </header>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        {notice && (
          <p role="status" className="notice">
            {notice}
          </p>
        )}
        {page === "Overview" && (
          <>
            <p className="intro">
              Review workforce readiness before assigning high-risk work.
            </p>
            <div className="metrics">
              {[
                ["Workers", workers.length],
                [
                  "Valid certificates",
                  rows("certificate").filter(
                    (c) =>
                      c.data.status === "VALID" &&
                      new Date(c.data.expiresAt) > new Date(),
                  ).length,
                ],
                [
                  "Practical reviews",
                  rows("attempt").filter(
                    (a) => a.data.status === "AWAITING_PRACTICAL_REVIEW",
                  ).length,
                ],
                [
                  "Open emergencies",
                  rows("emergency").filter((e) => e.data.status === "RECEIVED")
                    .length,
                ],
              ].map(([label, value]) => (
                <section className="panel" key={label}>
                  <p>{label}</p>
                  <strong className="metric">{value}</strong>
                </section>
              ))}
            </div>
            <section className="panel">
              <h2>Assignment compliance</h2>
              {!data.compliance.length && (
                <Empty>No worker assignments to review.</Empty>
              )}
              {data.compliance.map((w) => (
                <div className="record" key={w.workerId}>
                  <strong>{w.name}</strong>
                  {w.jobs.length ? (
                    w.jobs.map((j) => (
                      <p key={j.jobId}>
                        <Badge>{j.missing.length ? "BLOCKED" : "VALID"}</Badge>{" "}
                        {j.missing.length
                          ? `Missing: ${j.missing.map(moduleName).join(", ")}`
                          : "Required certificates cover the shift."}
                      </p>
                    ))
                  ) : (
                    <p>No assigned jobs</p>
                  )}
                </div>
              ))}
            </section>
            <p className="muted">
              The console refreshes every 30 seconds. Emergency dispatch
              requires your site's staffed response process.
            </p>
          </>
        )}
        {page === "Workers" && (
          <>
            <section className="panel">
              <h2>Worker directory</h2>
              {workers.map((w) => (
                <div className="record" key={w.id}>
                  <strong>{w.name}</strong>
                  <p>
                    {w.employeeId} • {w.site} • {friendly(w.role)}
                  </p>
                </div>
              ))}
            </section>
            {admin && (
              <Form
                title="Add employee"
                busy={busy}
                submit={(p) =>
                  run(async () => {
                    await request("workers", p);
                    await reload();
                    setNotice(
                      "Employee created. They can now sign in using their registered phone.",
                    );
                  })
                }
              >
                <Field label="Employee ID" name="employeeId" maxLength={50} />
                <Field label="Full name" name="name" maxLength={100} />
                <Field
                  label="Phone (E.164, e.g. +91…)"
                  name="phone"
                  type="tel"
                  pattern="\+[1-9][0-9]{7,14}"
                />
                <Field label="Site" name="site" defaultValue={data.user.site} />
                <Field
                  label="Role"
                  name="role"
                  options={[
                    "WORKER",
                    "SUPERVISOR",
                    "TRAINER",
                    "SAFETY_OFFICER",
                    "HR",
                    "SITE_ADMIN",
                    "ORG_ADMIN",
                  ]}
                />
              </Form>
            )}
          </>
        )}
        {page === "Pending Approvals" && (
          <section className="panel">
            <h2>Pending Worker Self-Registrations</h2>
            <p>Approve or reject self-registered workers to enable their login access.</p>
            {workers.filter((w) => w.active === false).length === 0 ? (
              <p>No worker registrations currently pending approval.</p>
            ) : (
              workers.filter((w) => w.active === false).map((w) => (
                <div className="record" key={w.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <h3>{w.name}</h3>
                    <p>{w.employeeId} • {w.site} • {w.phone}</p>
                  </div>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button
                      className="primary"
                      onClick={() =>
                        run(async () => {
                          await request(`admin/workers/${w.id}/approve`, {});
                          await reload();
                          setNotice(`Approved worker account for ${w.name}.`);
                        })
                      }
                    >
                      Approve
                    </button>
                    <button
                      onClick={() =>
                        run(async () => {
                          await request(`admin/workers/${w.id}/reject`, {});
                          await reload();
                          setNotice(`Rejected registration for ${w.name}.`);
                        })
                      }
                    >
                      Reject
                    </button>
                  </div>
                </div>
              ))
            )}
          </section>
        )}
        {page === "Training" && (
          <>
            <section className="panel">
              <h2>Available modules</h2>
              {data.modules.map((m) => (
                <div className="record" key={m.id}>
                  <h3>{m.title}</h3>
                  <p>{m.description}</p>
                  <small>
                    {m.minutes} minutes • Version {m.version} • English
                  </small>
                </div>
              ))}
            </section>
            {can(["TRAINER", "SAFETY_OFFICER", "SUPERVISOR", "ORG_ADMIN"]) && (
              <Form
                title="Assign training"
                busy={busy}
                submit={(p) => operation("training.assign", p)}
              >
                <Field label="Worker" name="workerId" options={options} />
                <Field
                  label="Module"
                  name="moduleId"
                  options={data.modules.map((m) => ({
                    value: m.id,
                    label: m.title,
                  }))}
                />
                <Field label="Due date" name="due" type="date" />
              </Form>
            )}
            <Records
              rows={rows("trainingAssignment")}
              title={(r) => moduleName(r.data.moduleId)}
              detail={(r) => `${workerName(r.owner_id)} • Due ${r.data.due}`}
            />
            <h2>Employee training history</h2>
            <p>
              Completed sequences appear after the worker saves and
              synchronizes. Assessments and certificates are reviewed
              separately.
            </p>
            <Records
              rows={rows("progress")}
              title={(r) =>
                `${workerName(r.owner_id)} • ${moduleName(r.data.moduleId)}`
              }
              detail={(r) =>
                `${r.data.mode} • ${r.data.durationSeconds}s • Completed ${time(r.data.completedAt)}`
              }
            />
          </>
        )}
        {page === "Assessments" && (
          <>
            <p className="intro">
              Review practical competence in person. An app score alone does not
              establish job readiness.
            </p>
            {rows("attempt").map((a) => (
              <section className="panel" key={a.id}>
                <h2>
                  {workerName(a.owner_id)} • {moduleName(a.data.moduleId)}
                </h2>
                <Badge>{a.data.status}</Badge>
                <p>
                  Score {a.data.score}% • Attempt {a.data.attemptNumber} •{" "}
                  {friendly(a.data.practicalMode)}
                </p>
                <p>
                  Topics to review: {a.data.weakTopics.join(", ") || "None"}
                </p>
                {a.data.status === "AWAITING_PRACTICAL_REVIEW" &&
                  can(["TRAINER", "SAFETY_OFFICER", "ORG_ADMIN"]) && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        operation("attempt.approve", { attemptId: a.id });
                      }}
                    >
                      <label className="check">
                        <input required type="checkbox" /> I observed and
                        approved this worker's practical competence under the
                        site's training procedure.
                      </label>
                      <button disabled={busy}>
                        Approve and issue certificate
                      </button>
                    </form>
                  )}
              </section>
            ))}
            {!rows("attempt").length && (
              <Empty>No assessments submitted.</Empty>
            )}
          </>
        )}
        {page === "Certificates" && (
          <>
            {rows("certificate").map((c) => (
              <section className="panel" key={c.id}>
                <h2>
                  {workerName(c.owner_id)} • {moduleName(c.data.moduleId)}
                </h2>
                <Badge>
                  {c.data.status === "REVOKED"
                    ? "REVOKED"
                    : new Date(c.data.expiresAt) <= new Date()
                      ? "EXPIRED"
                      : "VALID"}
                </Badge>
                <p>Expires {time(c.data.expiresAt)}</p>
                <a href={`/verify/${c.id}`} target="_blank" rel="noreferrer">
                  Open public verification
                </a>
                {c.data.status !== "REVOKED" &&
                  can(["SAFETY_OFFICER", "ORG_ADMIN"]) && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        operation("certificate.revoke", {
                          certificateId: c.id,
                          reason: new FormData(e.currentTarget).get("reason"),
                        });
                      }}
                    >
                      <Field label="Revocation reason" name="reason" />
                      <button className="danger-button" disabled={busy}>
                        Revoke certificate
                      </button>
                    </form>
                  )}
              </section>
            ))}
            {!rows("certificate").length && (
              <Empty>No certificates issued.</Empty>
            )}
          </>
        )}
        {page === "Jobs" && (
          <>
            {can(["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"]) && (
              <Form
                title="Create a job / shift"
                busy={busy}
                submit={(p) =>
                  operation("job.create", {
                    title: p.title,
                    site: p.site,
                    start: new Date(p.start).toISOString(),
                    end: new Date(p.end).toISOString(),
                    requirements:
                      p.requirements === "both"
                        ? ["fire", "gas"]
                        : [p.requirements],
                    ppe: p.ppe,
                    tasks: p.tasks
                      .split(";")
                      .map((s) => s.trim())
                      .filter(Boolean),
                  })
                }
              >
                <Field label="Job title" name="title" />
                <Field label="Site" name="site" defaultValue={data.user.site} />
                <Field label="Shift start" name="start" type="datetime-local" />
                <Field label="Shift end" name="end" type="datetime-local" />
                <Field
                  label="Required qualifications"
                  name="requirements"
                  options={[
                    { value: "fire", label: "Fire safety" },
                    { value: "gas", label: "Gas / confined space" },
                    { value: "both", label: "Both modules" },
                  ]}
                />
                <Field label="Required PPE" name="ppe" />
                <Field label="Tasks (separate with semicolons)" name="tasks" />
              </Form>
            )}
            {rows("job").map((j) => (
              <section className="panel" key={j.id}>
                <h2>{j.data.title}</h2>
                <Badge>{j.data.status}</Badge>
                <p>
                  {j.data.site} • {time(j.data.start)} — {time(j.data.end)}
                </p>
                <p>Worker: {workerName(j.owner_id)}</p>
                <p>
                  Required: {j.data.requirements.map(moduleName).join(", ")}
                </p>
                <p>PPE: {j.data.ppe}</p>
                {!j.owner_id &&
                  can(["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"]) && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        operation("job.assign", {
                          jobId: j.id,
                          workerId: new FormData(e.currentTarget).get(
                            "workerId",
                          ),
                        });
                      }}
                    >
                      <Field
                        label="Assign worker"
                        name="workerId"
                        options={options}
                      />
                      <button disabled={busy}>
                        Check eligibility and assign
                      </button>
                    </form>
                  )}
              </section>
            ))}
          </>
        )}
        {page === "Tasks" &&
          rows("job").map((j) => (
            <section className="panel" key={j.id}>
              <h2>
                {j.data.title} • {workerName(j.owner_id)}
              </h2>
              {j.data.tasks.map((task, index) => (
                <div className="record" key={index}>
                  <strong>{task}</strong>
                  <p>{j.data.notes?.[index] ?? "No completion note yet."}</p>
                  <Badge>
                    {j.data.verifiedTasks.includes(index)
                      ? "APPROVED"
                      : j.data.completedTasks.includes(index)
                        ? "SUBMITTED"
                        : "PENDING"}
                  </Badge>
                  {j.data.completedTasks.includes(index) &&
                    !j.data.verifiedTasks.includes(index) &&
                    can(["SUPERVISOR", "SITE_ADMIN", "ORG_ADMIN"]) && (
                      <button
                        disabled={busy}
                        onClick={() =>
                          operation("task.verify", { jobId: j.id, index })
                        }
                      >
                        Verify completion
                      </button>
                    )}
                </div>
              ))}
            </section>
          ))}
        {page === "Attendance" && (
          <>
            <Records
              rows={rows("attendance")}
              title={(r) => workerName(r.owner_id)}
              detail={(r) =>
                `In: ${time(r.data.checkIn)} • Out: ${time(r.data.checkOut)} • ${r.data.minutes ?? "Active"} minutes`
              }
            />
            {rows("attendanceClaim").map((c) => (
              <section className="panel" key={c.id}>
                <h2>Offline claim • {workerName(c.owner_id)}</h2>
                <Badge>{c.data.status}</Badge>
                <p>
                  {time(c.data.checkIn)} – {time(c.data.checkOut)}
                </p>
                <p>{c.data.reason}</p>
                {c.data.status === "PENDING" &&
                  can(["SUPERVISOR", "HR", "ORG_ADMIN"]) && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        const p = Object.fromEntries(
                          new FormData(e.currentTarget),
                        );
                        operation("attendance.review", { claimId: c.id, ...p });
                      }}
                    >
                      <Field
                        label="Claim decision"
                        name="status"
                        options={["APPROVED", "REJECTED"]}
                      />
                      <Field
                        label="Evidence checked / decision reason"
                        name="reason"
                      />
                      <button disabled={busy}>Review offline claim</button>
                    </form>
                  )}
              </section>
            ))}
          </>
        )}
        {page === "Leave" && (
          <>
            {rows("leave").map((l) => (
              <section className="panel" key={l.id}>
                <h2>
                  {workerName(l.owner_id)} • {l.data.type}
                </h2>
                <Badge>{l.data.status}</Badge>
                <p>
                  {l.data.start} → {l.data.end} • {l.data.days} days
                </p>
                <p>{l.data.reason}</p>
                {l.data.status === "PENDING" &&
                  can(["SUPERVISOR", "HR", "ORG_ADMIN"]) && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        const p = Object.fromEntries(
                          new FormData(e.currentTarget),
                        );
                        operation("leave.decide", { leaveId: l.id, ...p });
                      }}
                    >
                      <Field
                        label="Decision"
                        name="status"
                        options={["APPROVED", "REJECTED"]}
                      />
                      <Field label="Decision reason" name="reason" />
                      <button disabled={busy}>Save decision</button>
                    </form>
                  )}
              </section>
            ))}
            {!rows("leave").length && <Empty>No leave requests.</Empty>}
          </>
        )}
        {page === "Payroll" && (
          <>
            <Form
              title="Publish payroll record"
              busy={busy}
              submit={(p) =>
                operation("payroll.publish", {
                  ...p,
                  ...Object.fromEntries(
                    ["base", "overtime", "incentives", "deductions"].map(
                      (k) => [k, Math.round(Number(p[k]) * 100)],
                    ),
                  ),
                })
              }
            >
              <Field label="Worker" name="workerId" options={options} />
              <Field label="Month" name="month" type="month" />
              {["base", "overtime", "incentives", "deductions"].map((k) => (
                <Field
                  key={k}
                  label={`${friendly(k)} (INR)`}
                  name={k}
                  type="number"
                  min="0"
                  step="0.01"
                  defaultValue="0"
                />
              ))}
              <Field
                label="Payment status"
                name="status"
                options={["PENDING", "PAID"]}
              />
            </Form>
            <Records
              rows={rows("payroll")}
              title={(r) => `${workerName(r.owner_id)} • ${r.data.month}`}
              detail={(r) => `${money(r.data.net)} • ${r.data.status}`}
            />
          </>
        )}
        {page === "Emergencies" && (
          <>
            <p className="intro">
              Acknowledge only after taking responsibility for the site's
              emergency response. Alerts may have been delayed by offline
              connectivity.
            </p>
            {rows("emergency").map((e) => (
              <section className="panel emergency" key={e.id}>
                <h2>
                  {friendly(e.data.type)} • {e.data.site}
                </h2>
                <Badge>{e.data.status}</Badge>
                <p>
                  {workerName(e.owner_id)} • Received {time(e.data.createdAt)}
                </p>
                <p>{e.data.message || "No additional message"}</p>
                {e.data.status === "RECEIVED" &&
                  can([
                    "SUPERVISOR",
                    "SAFETY_OFFICER",
                    "SITE_ADMIN",
                    "ORG_ADMIN",
                  ]) && (
                    <button
                      className="danger-button"
                      disabled={busy}
                      onClick={() =>
                        operation("emergency.acknowledge", {
                          emergencyId: e.id,
                        })
                      }
                    >
                      Acknowledge response
                    </button>
                  )}
              </section>
            ))}
            {!rows("emergency").length && (
              <Empty>No emergency alerts received.</Empty>
            )}
          </>
        )}
        {page === "Audit" && (
          <section className="panel">
            <h2>Recent audit events</h2>
            {audit.map((a) => (
              <div className="record" key={a.id}>
                <strong>{a.action}</strong>
                <p>
                  {workerName(a.actor_id)} • {time(a.created_at)}
                </p>
                <small>{a.record_id}</small>
              </div>
            ))}
          </section>
        )}
      </main>
    </div>
  );
}
function Records({ rows, title, detail }) {
  return (
    <section className="panel">
      {rows.length ? (
        rows.map((r) => (
          <div className="record" key={r.id}>
            <h3>{title(r)}</h3>
            <p>{detail(r)}</p>
          </div>
        ))
      ) : (
        <Empty>No records available.</Empty>
      )}
    </section>
  );
}
createRoot(document.getElementById("root")).render(<App />);
