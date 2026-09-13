# API contract

All private endpoints require `Authorization: Bearer <accessToken>`. Browser clients use the same origin. JSON request bodies are limited to 64 KiB. Tokens are never accepted in query parameters.

| Method/path | Purpose |
|---|---|
| `GET /health` | Database connectivity health check |
| `POST /api/auth/request` | `{organization, employeeId}` → opaque challenge ID |
| `POST /api/auth/verify` | `{challengeId, code}` → access token, refresh token, user |
| `POST /api/auth/refresh` | `{refreshToken}` → rotated credentials |
| `POST /api/auth/logout` | Revoke current session |
| `GET /api/bootstrap` | Current user's filtered profile, records, question catalog, staff-visible workers and compliance |
| `POST /api/operations` | Validated, idempotent domain command |
| `POST /api/workers` | Organization-admin employee provisioning |
| `POST /api/devices` | `{token}` → bind push registration to authenticated user/session |
| `GET /api/audit` | Organization-admin audit feed (most recent 200) |
| `GET /api/certificates/:uuid/verify` | Public current credential status, no worker PII |
| `GET /verify/:uuid` | Human-readable verification page |

## Commands

```json
{
  "id": "CLIENT-GENERATED-UUID",
  "type": "leave.apply",
  "payload": {
    "type": "CASUAL",
    "start": "2027-01-10",
    "end": "2027-01-11",
    "reason": "Family appointment"
  }
}
```

Reuse the same UUID for a retry of the same operation. A successful replay returns the original result. Reusing an ID with different validated content returns a conflict. Rejected operations are not recorded as successful operations.

The executable request schemas and role map are in `backend/src/domain.js`. Supported commands:

| Command | Key payload fields |
|---|---|
| `training.complete` | `moduleId`, `version`, ordered `steps`, `durationSeconds`, `mode` |
| `assessment.submit` | `moduleId`, five answer indices; client scores are rejected |
| `attempt.approve` | `attemptId` |
| `certificate.revoke` | `certificateId`, `reason` |
| `job.create` | `title`, `site`, UTC `start`/`end`, `requirements`, `ppe`, `tasks` |
| `job.assign` | `jobId`, `workerId` |
| `attendance.in` / `attendance.out` | `jobId` / `attendanceId` |
| `attendance.claim` | `jobId`, historical UTC `checkIn`/`checkOut`, `reason` |
| `attendance.review` | `claimId`, `status`, `reason` |
| `task.complete` | `jobId`, `index`, `note` |
| `task.verify` | `jobId`, `index` |
| `leave.apply` | `type`, ISO `start`/`end` dates, `reason` |
| `leave.decide` | `leaveId`, `status`, `reason` |
| `payroll.publish` | `workerId`, `month`, integer paise amounts `base`, `overtime`, `incentives`, `deductions`, `status` |
| `emergency.raise` | `type`, `message`, optional `latitude`/`longitude` |
| `emergency.acknowledge` | `emergencyId` |
| `training.assign` | `workerId`, `moduleId`, `due` |
| `notification.read` | `notificationId` |

An offline client retains its operation ID and payload until acknowledged. A rejected operation is displayed for review rather than silently dropped. Shift authorization and certificate issuance remain online/server-controlled. Push delivery is at least once; the notification record ID provides stable device notification replacement.

