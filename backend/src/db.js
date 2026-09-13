import pg from "pg";
export function database(url) {
  const pool = new pg.Pool({
    connectionString: url,
    max: 10,
    statement_timeout: 10000,
  });
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
