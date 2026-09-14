import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { runtimeConfig } from "../src/config.js";
import { databaseOptions } from "../src/db.js";

const base = {
  NODE_ENV: "production",
  DATABASE_URL: "postgres://test:test@db/test",
  RENDER_EXTERNAL_URL: "https://example.onrender.com",
  PORT: "10000",
  PROXY_HOPS: "1",
};
test("Render origin and dynamic port work without a hard-coded public URL", () => {
  assert.deepEqual(runtimeConfig(base), {
    publicUrl: "https://example.onrender.com",
    port: 10000,
    proxyHops: 1,
  });
  assert.equal(
    runtimeConfig({ ...base, PUBLIC_URL: "https://safety.example.com/" })
      .publicUrl,
    "https://safety.example.com",
  );
});
test("production rejects unsafe origins, invalid ports and unbounded proxy trust", () => {
  for (const PUBLIC_URL of [
    "http://example.com",
    "https://localhost",
    "https://user:password@example.com",
    "https://example.com/path",
    "https://example.com/?secret=x",
    "https://example.com/#fragment",
  ])
    assert.throws(() => runtimeConfig({ ...base, PUBLIC_URL }));
  for (const PORT of ["0", "65536", "NaN", "1.5"])
    assert.throws(() => runtimeConfig({ ...base, PORT }));
  assert.throws(() => runtimeConfig({ ...base, PROXY_HOPS: "true" }));
});
test("custom database CA keeps certificate verification and overrides URL SSL parameters", () => {
  const dir = mkdtempSync(join(tmpdir(), "suraksha-ca-"));
  try {
    const file = join(dir, "ca.pem");
    writeFileSync(file, "test-ca");
    const options = databaseOptions(
      "postgres://u:p@db/test?sslmode=require&sslrootcert=ignored&uselibpqcompat=true",
      { DATABASE_CA_CERT: file },
    );
    assert.deepEqual(options.ssl, { ca: "test-ca", rejectUnauthorized: true });
    assert.equal(new URL(options.connectionString).search, "");
    assert.equal(options.connectionTimeoutMillis, 10000);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
test("standard database TLS URL is preserved and missing configuration fails closed", () => {
  const url = "postgres://u:p@db/test?sslmode=verify-full";
  assert.equal(databaseOptions(url, {}).connectionString, url);
  assert.throws(() => databaseOptions(undefined, {}));
  assert.throws(() => databaseOptions("https://db", {}));
  assert.throws(() =>
    databaseOptions(url, { DATABASE_CA_CERT: "missing-ca.pem" }),
  );
});
