import pg from "pg";
import { readFileSync } from "node:fs";
export function databaseOptions(url, env = process.env) {
  if (!url) throw new Error("DATABASE_URL is required.");
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    throw new Error("DATABASE_URL must be a PostgreSQL URL.");
  }
  if (!["postgres:", "postgresql:"].includes(parsed.protocol))
    throw new Error("DATABASE_URL must be a PostgreSQL URL.");
  let ssl;
  if (env.DATABASE_CA_CERT) {
    // URL TLS parameters override pg's ssl object. Remove them when using a trusted provider CA.
    for (const key of [
      "sslmode",
      "sslcert",
      "sslkey",
      "sslrootcert",
      "uselibpqcompat",
      "ssl",
    ])
      parsed.searchParams.delete(key);
    ssl = {
      ca: readFileSync(env.DATABASE_CA_CERT, "utf8"),
      rejectUnauthorized: true,
    };
  }
  return {
    connectionString: parsed.toString(),
    ...(ssl ? { ssl } : {}),
    max: 10,
    connectionTimeoutMillis: 10000,
    statement_timeout: 10000,
  };
}
export function database(url) {
  const pool = new pg.Pool(databaseOptions(url));
  return {
    query: (sql, args) => pool.query(sql, args),
    close: () => pool.end(),
    async transaction(fn) {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const value = await fn(client);
        await client.query("COMMIT");
        return value;
      } catch (e) {
        await client.query("ROLLBACK");
        throw e;
      } finally {
        client.release();
      }
    },
  };
}
