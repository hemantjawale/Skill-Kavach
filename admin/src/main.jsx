import React, { useEffect, useState, useRef } from "react";
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
  const reloadSequence = useRef(0);
  async function reload() {
    const sequence = ++reloadSequence.current;
    const d = await request("bootstrap");
    if (!Array.isArray(d.workers))
      throw Error(
        "The server did not return an employee directory. Check the deployed backend version.",
      );
    if (sequence === reloadSequence.current) setData(d);
    return d;
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
      reload().catch((e) => setError(e.message));
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
              label="Registered email address"
              name="email"
              type="email"
              autoComplete="email"
            />
            <p>
              Use the email registered for your staff account. Workers sign in
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
              If the staff account and email match, a code was sent to that
              email address.
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

  // Filter States
  const [filterSite, setFilterSite] = useState("ALL");
  const [filterModule, setFilterModule] = useState("ALL");
  const [filterCertStatus, setFilterCertStatus] = useState("ALL");
  const [filterAssessmentStatus, setFilterAssessmentStatus] = useState("ALL");
  const [filterLang, setFilterLang] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  const rows = (kind) => records.filter((r) => r.kind === kind);
  const workerName = (id) =>
    workers.find((w) => w.id === id)?.name ??
    (id === data.user.id ? data.user.name : "Unassigned");
  const moduleName = (id) => data.modules.find((m) => m.id === id)?.title ?? id;

  const sitesList = Array.from(new Set(workers.map((w) => w.site).filter(Boolean)));
  const options = workers
    .filter((w) => w.role === "WORKER" && w.active !== false)
    .map((w) => ({ value: w.id, label: `${w.name} • ${w.employeeId}` }));
  const can = (roles) => roles.includes(data.user.role);
  const admin = can(["ORG_ADMIN"]);

  // Top-Level 10 KPIs Calculation
  const totalWorkers = workers.filter((w) => w.role === "WORKER").length;
  const trainedWorkerIds = new Set(rows("progress").map((p) => p.owner_id));
  const workersTrainedCount = trainedWorkerIds.size;
  const pendingAssignments = rows("trainingAssignment").filter((t) => !trainedWorkerIds.has(t.owner_id));
  const workersPendingCount = new Set(pendingAssignments.map((t) => t.owner_id)).size;

  const allAttempts = rows("attempt");
  const passedAttemptsCount = allAttempts.filter((a) => a.data.status === "APPROVED" || a.data.score >= 80).length;
  const failedAttemptsCount = allAttempts.filter((a) => a.data.status === "FAILED" || (a.data.score > 0 && a.data.score < 80)).length;

  const now = new Date();
  const thirtyDaysLater = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
  const allCerts = rows("certificate");
  const activeCerts = allCerts.filter((c) => c.data.status === "VALID" && new Date(c.data.expiresAt) > now);
  const expiredCerts = allCerts.filter((c) => c.data.status === "REVOKED" || new Date(c.data.expiresAt) <= now);
  const expiringSoonCerts = allCerts.filter((c) => c.data.status === "VALID" && new Date(c.data.expiresAt) > now && new Date(c.data.expiresAt) <= thirtyDaysLater);

  const moduleCompletionRate = totalWorkers > 0 ? Math.round((workersTrainedCount / totalWorkers) * 100) : 0;
  const overallComplianceRate = totalWorkers > 0 ? Math.round((activeCerts.length / totalWorkers) * 100) : 0;

  // Site-wise compliance breakdown
  const siteCompliance = sitesList.map((s) => {
    const siteWorkers = workers.filter((w) => w.site === s && w.role === "WORKER");
    const siteCerts = activeCerts.filter((c) => {
      const w = workers.find((w) => w.id === c.owner_id);
      return w && w.site === s;
    });
    const rate = siteWorkers.length > 0 ? Math.round((siteCerts.length / siteWorkers.length) * 100) : 0;
    return { site: s, total: siteWorkers.length, certs: siteCerts.length, rate };
  });

  // CSV Compliance Export Function
  function exportComplianceCsv() {
    const headers = ["Worker ID", "Employee ID", "Name", "Site", "Role", "Language", "Certificates", "Status"];
    const csvRows = [headers.join(",")];
    workers.forEach((w) => {
      const workerCerts = activeCerts.filter((c) => c.owner_id === w.id);
      const certNames = workerCerts.map((c) => c.data.moduleId).join("; ");
      const statusStr = w.active === false ? "PENDING APPROVAL" : workerCerts.length > 0 ? "COMPLIANT" : "NON_COMPLIANT";
      csvRows.push([w.id, w.employeeId, `"${w.name}"`, `"${w.site}"`, w.role, w.preferredLanguage || "en", `"${certNames}"`, statusStr].join(","));
    });
    const blob = new Blob([csvRows.join("\n")], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `compliance_report_${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // Filtered Lists
  const filteredWorkers = workers.filter((w) => {
    if (filterSite !== "ALL" && w.site !== filterSite) return false;
    if (filterLang !== "ALL" && w.preferredLanguage !== filterLang) return false;
    if (searchQuery && !w.name.toLowerCase().includes(searchQuery.toLowerCase()) && !w.employeeId.toLowerCase().includes(searchQuery.toLowerCase())) return false;
    return true;
  });

  const filteredCerts = allCerts.filter((c) => {
    if (filterModule !== "ALL" && c.data.moduleId !== filterModule) return false;
    if (filterCertStatus === "VALID" && (c.data.status !== "VALID" || new Date(c.data.expiresAt) <= now)) return false;
    if (filterCertStatus === "EXPIRED" && new Date(c.data.expiresAt) > now) return false;
    if (filterCertStatus === "REVOKED" && c.data.status !== "REVOKED") return false;
    return true;
  });

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
        <p className="subbrand">Workforce safety compliance</p>
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
            <p className="eyebrow">{data.user.site} / COMPLIANCE OPERATIONS</p>
            <h1>{page}</h1>
          </div>
          <div style={{ display: "flex", gap: "12px" }}>
            <button className="export-button" onClick={exportComplianceCsv}>
              Export CSV Report
            </button>
            <button
              className="secondary"
              disabled={busy}
              onClick={() => run(reload)}
            >
              Refresh
            </button>
          </div>
        </header>

        {/* Global Multi-dimensional Filter Bar */}
        <div className="filter-bar">
          <input
            type="text"
            placeholder="Search worker name / ID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <select value={filterSite} onChange={(e) => setFilterSite(e.target.value)}>
            <option value="ALL">All Sites</option>
            {sitesList.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <select value={filterModule} onChange={(e) => setFilterModule(e.target.value)}>
            <option value="ALL">All Modules</option>
            {data.modules.map((m) => (
              <option key={m.id} value={m.id}>{m.title}</option>
            ))}
          </select>
          <select value={filterCertStatus} onChange={(e) => setFilterCertStatus(e.target.value)}>
            <option value="ALL">All Cert Statuses</option>
            <option value="VALID">VALID</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="REVOKED">REVOKED</option>
          </select>
          <select value={filterLang} onChange={(e) => setFilterLang(e.target.value)}>
            <option value="ALL">All Languages</option>
            <option value="en">English</option>
            <option value="hi">Hindi (हिन्दी)</option>
            <option value="sat">Santali (ᱥᱟᱱᱛᱟᱲᱤ)</option>
          </select>
        </div>

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
            {/* Documented Site Compliance Indicator */}
            <div
              className={`compliance-banner ${
                overallComplianceRate >= 80 ? "high" : overallComplianceRate >= 50 ? "moderate" : "critical"
              }`}
            >
              Documented Site Compliance: {overallComplianceRate}% —{" "}
              {overallComplianceRate >= 80
                ? "HIGH COMPLIANCE (Workforce exceeds 80% safety qualification target)"
                : overallComplianceRate >= 50
                ? "MODERATE COMPLIANCE (Refresher drills required for uncertified shifts)"
                : "CRITICAL ACTION REQUIRED (Less than 50% qualified — high risk)"}
            </div>

            {/* Top-Level 10 KPI Metrics Cards */}
            <div className="kpi-grid">
              <div className="kpi-card">
                <p>Total Workers</p>
                <div className="metric">{totalWorkers}</div>
              </div>
              <div className="kpi-card">
                <p>Workers Trained</p>
                <div className="metric">{workersTrainedCount}</div>
              </div>
              <div className="kpi-card">
                <p>Pending Training</p>
                <div className="metric">{workersPendingCount}</div>
              </div>
              <div className="kpi-card">
                <p>Passed Assessments</p>
                <div className="metric" style={{ color: "#2e7d4f" }}>{passedAttemptsCount}</div>
              </div>
              <div className="kpi-card">
                <p>Failed Assessments</p>
                <div className="metric" style={{ color: "#c63d3d" }}>{failedAttemptsCount}</div>
              </div>
              <div className="kpi-card">
                <p>Active Certificates</p>
                <div className="metric" style={{ color: "#2e7d4f" }}>{activeCerts.length}</div>
              </div>
              <div className="kpi-card">
                <p>Expired Certificates</p>
                <div className="metric" style={{ color: "#c63d3d" }}>{expiredCerts.length}</div>
              </div>
              <div className="kpi-card">
                <p>Expiring Soon (30d)</p>
                <div className="metric" style={{ color: "#d97706" }}>{expiringSoonCerts.length}</div>
              </div>
              <div className="kpi-card">
                <p>Module Completion</p>
                <div className="metric">{moduleCompletionRate}%</div>
              </div>
              <div className="kpi-card">
                <p>Overall Compliance</p>
                <div className="metric">{overallComplianceRate}%</div>
              </div>
            </div>

            {/* Site-wise Compliance Breakdown */}
            <section className="panel">
              <h2>Site-Wise Safety Qualification Breakdown</h2>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "12px" }}>
                {siteCompliance.map((sc) => (
                  <div key={sc.site} className="panel" style={{ background: "#f8fafc", padding: "14px" }}>
                    <strong>{sc.site}</strong>
                    <p style={{ margin: "4px 0", fontSize: "14px" }}>
                      Qualified: {sc.certs} / {sc.total} ({sc.rate}%)
                    </p>
                    <Badge>{sc.rate >= 80 ? "HIGH" : sc.rate >= 50 ? "MODERATE" : "CRITICAL"}</Badge>
                  </div>
                ))}
              </div>
            </section>

            <section className="panel">
              <h2>Shift Assignment Compliance Checks</h2>
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
          </>
        )}

        {page === "Workers" && (
          <>
            <section className="panel">
              <h2>Employee Directory ({filteredWorkers.length} workers)</h2>
              <p>
                Organization: <strong>{data.user.organization}</strong>. Showing workers matching current filters.
              </p>
              {!filteredWorkers.length && (
                <Empty>No workers match the selected filter criteria.</Empty>
              )}
              {filteredWorkers.map((w) => {
                const wCerts = activeCerts.filter((c) => c.owner_id === w.id);
                return (
                  <div className="record" key={w.id}>
                    <strong>{w.name} ({w.employeeId})</strong>
                    <p>
                      Site: {w.site} • Role: {friendly(w.role)} • Preferred Language: {w.preferredLanguage || "en"}
                    </p>
                    <p>
                      Active Certificates: {wCerts.map((c) => moduleName(c.data.moduleId)).join(", ") || "None"}
                    </p>
                    <p>
                      <Badge>
                        {w.active === false
                          ? "PENDING APPROVAL"
                          : wCerts.length > 0
                          ? "QUALIFIED"
                          : "TRAINING NEEDED"}
                      </Badge>
                      <span className="origin-badge online">ONLINE DB</span>
                    </p>
                  </div>
                );
              })}
            </section>
            {admin && (
              <Form
                title="Add employee"
                busy={busy}
                submit={(p) =>
                  run(async () => {
                    const created = await request("workers", p);
                    setNotice(`Employee ${p.employeeId} saved.`);
                    await reload();
                  })
                }
              >
                <Field label="Employee ID" name="employeeId" maxLength={50} />
                <Field label="Full name" name="name" maxLength={100} />
                <Field label="Email address" name="email" type="email" />
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
            <p>Approve or reject self-registered workers to enable access.</p>
            {workers.filter((w) => w.active === false).length === 0 ? (
              <Empty>No worker registrations currently pending approval.</Empty>
            ) : (
              workers
                .filter((w) => w.active === false)
                .map((w) => (
                  <div className="record" key={w.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <h3>{w.name}</h3>
                      <p>{w.employeeId} • {w.site} • {w.email || "No email"}</p>
                    </div>
                    <div style={{ display: "flex", gap: "8px" }}>
                      <button className="primary" onClick={() => run(async () => { await request(`admin/workers/${w.id}/approve`, {}); await reload(); })}>
                        Approve
                      </button>
                      <button onClick={() => run(async () => { await request(`admin/workers/${w.id}/reject`, {}); await reload(); })}>
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
              <h2>Training Modules Breakdown</h2>
              {data.modules.map((m) => {
                const assigned = rows("trainingAssignment").filter((t) => t.data.moduleId === m.id).length;
                const completed = rows("progress").filter((p) => p.data.moduleId === m.id).length;
                const passed = allAttempts.filter((a) => a.data.moduleId === m.id && (a.data.status === "APPROVED" || a.data.score >= 80)).length;
                const failed = allAttempts.filter((a) => a.data.moduleId === m.id && (a.data.status === "FAILED" || (a.data.score > 0 && a.data.score < 80))).length;

                return (
                  <div className="record" key={m.id}>
                    <h3>{m.title}</h3>
                    <p>{m.description}</p>
                    <div style={{ display: "flex", gap: "16px", marginTop: "8px" }}>
                      <span><strong>Assigned:</strong> {assigned}</span>
                      <span><strong>Completed:</strong> {completed}</span>
                      <span style={{ color: "#2e7d4f" }}><strong>Passed:</strong> {passed}</span>
                      <span style={{ color: "#c63d3d" }}><strong>Failed:</strong> {failed}</span>
                    </div>
                  </div>
                );
              })}
            </section>
            {can(["TRAINER", "SAFETY_OFFICER", "SUPERVISOR", "ORG_ADMIN"]) && (
              <Form title="Assign training" busy={busy} submit={(p) => operation("training.assign", p)}>
                <Field label="Worker" name="workerId" options={options} />
                <Field label="Module" name="moduleId" options={data.modules.map((m) => ({ value: m.id, label: m.title }))} />
                <Field label="Due date" name="due" type="date" />
              </Form>
            )}
          </>
        )}

        {page === "Assessments" && (
          <>
            <p className="intro">Review practical competence and attempt scores.</p>
            {rows("attempt").map((a) => (
              <section className="panel" key={a.id}>
                <h2>{workerName(a.owner_id)} • {moduleName(a.data.moduleId)}</h2>
                <Badge>{a.data.status}</Badge>
                <span className="origin-badge offline">OFFLINE QUEUED</span>
                <p>Score: {a.data.score}% • Mode: {friendly(a.data.practicalMode)}</p>
                <p>Weak Topics: {a.data.weakTopics?.join(", ") || "None"}</p>
              </section>
            ))}
            {!rows("attempt").length && <Empty>No assessments submitted.</Empty>}
          </>
        )}

        {page === "Certificates" && (
          <>
            {filteredCerts.map((c) => (
              <section className="panel" key={c.id}>
                <h2>{workerName(c.owner_id)} • {moduleName(c.data.moduleId)}</h2>
                <Badge>
                  {c.data.status === "REVOKED" ? "REVOKED" : new Date(c.data.expiresAt) <= now ? "EXPIRED" : "VALID"}
                </Badge>
                <span className="origin-badge online">CRYPTOGRAPHICALLY VERIFIED</span>
                <p>Certificate ID: <small>{c.id}</small></p>
                <p>Expires: {time(c.data.expiresAt)}</p>
                <a href={`/verify/${c.id}`} target="_blank" rel="noreferrer">Open Verification Link</a>
              </section>
            ))}
            {!filteredCerts.length && <Empty>No certificates match current filters.</Empty>}
          </>
        )}

        {page === "Audit" && (
          <section className="panel">
            <h2>Recent Audit Log Events</h2>
            {audit.map((a) => (
              <div className="record" key={a.id}>
                <strong>{a.action}</strong>
                <p>{workerName(a.actor_id)} • {time(a.created_at)}</p>
                <small>Record ID: {a.record_id}</small>
              </div>
            ))}
            {!audit.length && <Empty>No audit log records found.</Empty>}
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
