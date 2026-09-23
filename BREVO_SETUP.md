# Brevo setup on Render free

The backend now supports Brevo HTTPS delivery. This uses port 443, not SMTP. Email login remains matched against each employee's stored email.

## Where to paste the key

Open Render → your Skill-Kavach service → Environment → Add Environment Variable:

```dotenv
EMAIL_PROVIDER=brevo
BREVO_API_KEY=PASTE_YOUR_BREVO_API_KEY_HERE
BREVO_SENDER_EMAIL=YOUR_VERIFIED_BREVO_SENDER_EMAIL
BREVO_SENDER_NAME=SurakshaSetu
BOOTSTRAP_ADMIN_EMAIL=jatinbhosale428@gmail.com
BOOTSTRAP_SECOND_ADMIN_EMAIL=hemantjawale24@gmail.com
```

Use a Brevo API key, not an SMTP key. The sender must be verified/approved in your Brevo account. Do not put the key in the website, Android properties, Git, or chat. For local testing, place these same variables in `backend/.env`. Keep existing database/CA/JWT/OTP/Firebase settings. Old SMTP credentials are ignored when EMAIL_PROVIDER=brevo.

## Why the old form still asks for a mobile number

The email changes must reach GitHub and the frontend build must run on Render. Saving environment variables alone cannot update the form. The screenshot showing “Registered mobile number” is an old build; entering an email there fails its telephone-format validation.

1. Commit and push the current code changes, including `admin/src/main.jsx`, the backend migration/email files, and new files, to your connected branch. Do not commit `.env`.
2. Your Render Root Directory is `backend`. Use this Build Command:

```sh
npm --prefix ../admin install --include=dev && npm --prefix ../admin run build && npm install --omit=dev
```

3. For the first email deployment use this Start Command:

```sh
npm run migrate && npm run seed && npm start
```

4. Choose **Manual Deploy → Clear build cache & deploy**. Check the deployment commit is the one you just pushed. Migration preserves workforce records and revokes legacy sessions once. Seed sets unset administrator emails; it refuses to overwrite existing different emails.
5. After success, restore Start Command to `npm run migrate && npm start`.
6. Hard-refresh https://skill-kavach.onrender.com/manager with Ctrl+Shift+R. The field must say **Registered email address**. If it still says mobile number, verify the deployed commit and frontend build log; do not keep retrying the old form.

## Which login to use

| Organization (unless changed at original seed) | Employee ID | Email |
|---|---|---|
| demo | ADMIN | jatinbhosale428@gmail.com |
| demo | ADMIN2 | hemantjawale24@gmail.com |

The screenshot pairs ADMIN with the second email. After seeding, use **ADMIN2** for that email. These identities are not interchangeable.

Existing workers need an administrator to set their email in Workers → Set login email. New workers use email during registration. Install the previously generated email-login APK after deploying this backend.

## Delivery checks

Request a code and inspect Brevo transactional logs and the recipient inbox/spam folder. A successful API response means provider acceptance, not guaranteed inbox delivery. Check sender verification, account approval and available quota if rejected. Brevo failures return a generic 503 and consume the failed challenge; there is no fallback to SMS or another mailbox.

Brevo HTTP transport is covered by mocked tests. Your actual API key and inbox delivery have not been tested here.
