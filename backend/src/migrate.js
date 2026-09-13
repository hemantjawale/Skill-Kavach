import { readFile } from "node:fs/promises";
import { database } from "./db.js";
export const schema = await readFile(
  new URL("./schema.sql", import.meta.url),
  "utf8",
);
if (process.argv[1]?.endsWith("migrate.js")) {
  const db = database(process.env.DATABASE_URL);
  try {
    await db.query(schema);
    console.log("Schema applied");
  } finally {
    await db.close();
  }
}
