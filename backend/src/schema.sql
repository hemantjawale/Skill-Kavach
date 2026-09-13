CREATE TABLE IF NOT EXISTS organizations (id text PRIMARY KEY, name text NOT NULL);
CREATE TABLE IF NOT EXISTS users (
 id text PRIMARY KEY, org_id text NOT NULL REFERENCES organizations(id),
 employee_id text NOT NULL, name text NOT NULL, phone text NOT NULL,
 role text NOT NULL CHECK(role IN ('WORKER','SUPERVISOR','TRAINER','SAFETY_OFFICER','HR','SITE_ADMIN','ORG_ADMIN')),
 site text NOT NULL, active boolean NOT NULL DEFAULT true,
 UNIQUE(org_id,employee_id)
);
CREATE TABLE IF NOT EXISTS otp_challenges (
 id text PRIMARY KEY, user_id text REFERENCES users(id), digest text NOT NULL,
 expires_at timestamptz NOT NULL, attempts int NOT NULL DEFAULT 0, consumed boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS auth_throttles (identity_hash text PRIMARY KEY, requested_at timestamptz NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (
 id text PRIMARY KEY, user_id text NOT NULL REFERENCES users(id), refresh_hash text NOT NULL UNIQUE,
 expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS records (
 id text PRIMARY KEY, org_id text NOT NULL REFERENCES organizations(id),
 kind text NOT NULL, owner_id text REFERENCES users(id), data jsonb NOT NULL,
 version integer NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS records_scope ON records(org_id,kind,owner_id);
CREATE TABLE IF NOT EXISTS operations (
 user_id text NOT NULL REFERENCES users(id), id text NOT NULL, digest text NOT NULL, result jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,id)
);
CREATE TABLE IF NOT EXISTS audit_log (
 id text PRIMARY KEY, org_id text NOT NULL REFERENCES organizations(id), actor_id text NOT NULL REFERENCES users(id),
 action text NOT NULL, record_id text, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS device_tokens (
 token text PRIMARY KEY, user_id text NOT NULL REFERENCES users(id), session_id text NOT NULL REFERENCES sessions(id), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS push_jobs (
 notification_id text PRIMARY KEY REFERENCES records(id), attempts int NOT NULL DEFAULT 0,
 next_attempt timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz, last_error text
);
