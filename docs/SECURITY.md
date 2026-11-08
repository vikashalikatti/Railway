# Security

How the Railway app protects accounts and data (backend v1.5.0, frontend v1.9.0).

## Settings to set once (Spring Tool Suite: Run Configurations > Environment)

| Variable | What it is | If not set |
|---|---|---|
| `JWT_SECRET` | Signs log-in tokens. A long random value, at least 32 characters. | A random one is used each start, so everyone is logged out on restart. |
| `MAIL_PASSWORD` | Gmail App Password for OTP emails. | OTP emails fail. |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL log-in. | The placeholder values in `application.properties`. |
| `CORS_ORIGINS` | Web addresses allowed to call the API from a browser. | `http://localhost:5173,http://localhost:4173` |
| `DOCS_PUBLIC` | `true` turns on Swagger UI. | Off. |

A random secret can be made with PowerShell:
`[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))`

## Who can call what

Checked in one place (`SecurityConfiguration` + `JwtAuthFilter`) and again inside the services.

| Endpoints | Who |
|---|---|
| `POST /admin/login`, `POST /admin/create` (only while no admin exists) | anyone |
| everything else under `/admin` | a logged-in admin |
| sign-up, log-in, OTP, forgot/reset password, `GET /customer/stations` | anyone |
| everything else under `/customer` | a logged-in passenger |
| anything else | nobody |

A passenger's token gets 403 on admin endpoints. Bookings are only visible to, and cancellable by, their owner.

## Sessions

- Tokens last 1 hour and carry the role, purpose and a token version.
- `POST /customer/logout` and `POST /admin/logout` end the token on the server. `?everywhere=true`
  raises the account's token version, ending every session on every device.
- A password reset also raises the token version: all other sessions end.
- The reset token from the OTP step only works for `reset-password`, for 10 minutes, and the
  account comes from that token (the email in the request can't point it at someone else).
- The React app signs out when the token expires and after 30 minutes without activity, and
  drops a saved session that was edited by hand.

## Guessing and abuse

- 5 wrong passwords or OTPs for one account in 15 minutes lock it for 15 minutes; 30 wrong tries
  from one IP address lock that address (429 "Too many attempts").
- 5 wrong OTPs cancel the OTP; a new one has to be requested.
- An OTP email can be sent to an address at most once a minute.
- A wrong email and a wrong password take the same time and give the same message.

## Data

- Passwords are stored with BCrypt and never sent back (`WRITE_ONLY`); OTPs, photo bytes and token
  versions are never sent back either.
- Sign-up and admin creation copy only the fields a person may choose: a request can't set its own
  id, role, verified flag or OTP.
- Sign-up details are checked on the server with the same rules as the form; photos must really be
  JPG, PNG or WebP (checked by their first bytes) and at most 5 MB.
- Errors never include stack traces, SQL or exception text; SQL logging is off.
- Passwords and OTPs travel in the request body, not the URL (URLs end up in logs).

## Browser

- Built pages carry a Content-Security-Policy (scripts and API calls only to this site).
- Dev and preview servers send `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`
  and a Permissions-Policy. Use the same headers on the real web server.
- React escapes everything it shows; the app never uses `dangerouslySetInnerHTML`.
- Every text box has a length limit; no source maps are published.

## Still to do when going live

- Serve over HTTPS (tokens and passwords must not travel over plain HTTP).
- Use a real database user with only the rights the app needs, not root.
- If you run more than one server, move the lockouts and logged-out tokens to a shared store (Redis).
