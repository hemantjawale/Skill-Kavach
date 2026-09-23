# Security boundaries and operational assumptions

There is no guarantee that an application contains zero vulnerabilities. This document describes implemented controls and the validation still needed.

- The server resolves users from the active session on every request. Role and site are not taken from JWT claims supplied by a client.
- Access JWTs are audience/issuer/algorithm restricted and expire after 15 minutes. Refresh tokens are random, stored hashed, rotated, and tied to revocable server sessions. Refresh replay revokes that user's sessions.
- OTPs use cryptographic randomness, keyed hashes, five-minute expiry, five-attempt lockout, one-use consumption, account-identity cooldown and IP rate limits. Unknown identities receive an opaque challenge as well. Production rejects file-based OTP delivery.
- Queries are parameterized. Domain operations lock the organization row before evaluating and changing safety records. User-provided record IDs are resolved within the authenticated organization; staff site limits and record-kind visibility are checked separately.
- Server assessment keys are excluded from the mobile catalog. Scores do not directly create certificates. A separate authorized reviewer must approve practical competence; AR telemetry remains untrusted client evidence.
- Job eligibility requires valid, unrevoked certificates through the end of the shift and is checked again at check-in. Offline attendance is reviewed separately and never authorizes work.
- Public certificate verification exposes no name, phone, employee number or organization data. Verify worker identity separately when using a credential.
- Android encrypts tokens, cached records and queued payloads with AES-GCM using an Android Keystore key. Backups and transfers are excluded. Corrupt encrypted data is preserved for explicit recovery instead of silently discarded. Screenshots and screen recording are allowed for demonstrations.
- Release traffic is HTTPS only. Debug HTTP is restricted to the emulator host. The client does not install permissive trust managers or follow redirects with credentials.
- React credentials remain in memory. No token is stored in browser localStorage/sessionStorage. API authentication uses authorization headers rather than cookies; the API does not enable cross-origin credential access.
- FCM messages contain only a notification ID; lock-screen content is generic. Push tokens are tied to active user sessions. Notification delivery does not imply that an emergency responder acknowledged an alert.
- Secrets are external to source control. The production image runs as a non-root user; only Caddy exposes public ports. `PROXY_HOPS=1` assumes that exact single private gateway path.

Deployment requirements include protected database volumes/backups, restricted database credentials, request-rate/availability controls at the gateway, secure secret distribution, service-account least privilege, retained audit records, restoration exercises, real-device testing and independent security review.

The current API loads tenant records as an aggregate and serializes domain writes per organization. It is designed for an initial deployment; benchmark it against actual tenant size and expected concurrency before rollout. The in-process IP limiter should be supplemented with gateway/shared rate limiting when running multiple API replicas. Current audit entries identify actor/action/record and time; they are not a cryptographically tamper-evident external audit ledger.

The dependency override for `gaxios@6.7.1` selects patched `uuid@11.1.1` for Firebase Admin's optional storage dependency chain. That gaxios version uses the compatible CommonJS `v4()` interface. Re-evaluate/remove the override when the upstream dependency chain includes the fix itself.


Email OTP migration: only a stored, matching email can receive a login code. Request-supplied fallback recipients and debug OTP responses are prohibited. SMTP uses authenticated TLS with certificate verification and bounded timeouts. The additive migration revokes former sessions and codes once; authorized email changes revoke the affected account's sessions/codes. Legacy phone contacts are retained solely as historical data, never as an authentication fallback. Register trusted employee emails before enabling their login.
