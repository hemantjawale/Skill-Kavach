import { test } from "node:test";
import assert from "node:assert/strict";
import { PGlite } from "@electric-sql/pglite";
import { schema } from "../src/migrate.js";
test("email migration preserves legacy users and revokes former sessions only once", async () => {
  const db = new PGlite();
  try {
    const legacy = schema.split("-- Email is nullable")[0];
    await db.exec(legacy);
    await db.exec(
      "INSERT INTO organizations VALUES('demo','Demo'); INSERT INTO users(id,org_id,employee_id,name,phone,role,site) VALUES('old','demo','OLD','Existing','+919000000001','WORKER','A'); INSERT INTO sessions VALUES('s1','old','hash',now()+interval '1 day',false);",
    );
    await db.exec(schema);
    const user = (await db.query("SELECT * FROM users WHERE id='old'")).rows[0];
    assert.equal(user.phone, "+919000000001");
    assert.equal(user.email, null);
    assert.equal(user.employee_id, "OLD");
    assert.equal(
      (await db.query("SELECT revoked FROM sessions WHERE id='s1'")).rows[0]
        .revoked,
      true,
    );
    await db.exec(
      "UPDATE users SET email='old@example.test' WHERE id='old'; INSERT INTO sessions VALUES('s2','old','hash2',now()+interval '1 day',false);",
    );
    await db.exec(schema);
    assert.equal(
      (await db.query("SELECT revoked FROM sessions WHERE id='s2'")).rows[0]
        .revoked,
      false,
    );
    assert.equal(
      (await db.query("SELECT email FROM users WHERE id='old'")).rows[0].email,
      "old@example.test",
    );
  } finally {
    await db.close();
  }
});
