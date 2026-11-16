# Authentication

## First-run setup

While no admin exists, the frontend sends every route to `/setup` (`GET /api/setup` → `setupRequired`). `POST /api/setup` creates the admin and logs them in. After that the endpoint answers 409 and the setup page redirects to the login page. A partial unique index (`users_single_admin_uq`) guarantees a single admin, even with two setups at once.

There is no public registration: the admin creates all other accounts.

## Tokens

| | Access token | Refresh token |
|---|---|---|
| Format | JWT (RS256, SmallRye JWT) | 32 random bytes, base64url |
| Lifetime | 15 min (`JWT_ACCESS_TOKEN_TTL`) | 14 days (`REFRESH_TOKEN_TTL`) |
| Where in the browser | Memory only (`AuthService`) | httpOnly cookie `pb_refresh`, `Path=/api/auth`, `SameSite=Strict`, `Secure` unless `AUTH_COOKIE_SECURE=false` |
| Stored server-side | No | SHA-256 hash in `refresh_tokens` |

JWT claims: `sub` = user id, `upn` = username, `groups` = role (`USER`/`ADMIN`).

Flow:
1. Login/setup returns the access token in the body and sets the refresh cookie.
2. The HTTP interceptor sends `Authorization: Bearer …` on `/api` calls.
3. On a 401 it calls `POST /api/auth/refresh` once (shared between parallel requests) and retries. If that fails too, the session is cleared and the user goes to `/login`.
4. On page load the app calls refresh to restore the session from the cookie.

**Rotation:** every refresh revokes the used token and issues a new one. Presenting an already used token again revokes all of that user's refresh tokens, because it suggests the token was stolen.

**Several tabs:** tabs that load together all send the same cookie. The token's row is locked while a refresh runs, so they are handled one after the other, and a token that was replaced less than 10 seconds ago (`REFRESH_TOKEN_GRACE`) is still accepted: each tab gets its own new token. Only hashes are stored, so the first tab's token can't be handed out again; the browser keeps the cookie that arrives last and the others expire unused. `refresh_tokens.rotated_at` marks a token as replaced, as opposed to revoked by logout or password reset.

**Clean-up:** when a user gets a new refresh token (login, refresh), their expired ones are deleted. Revoked tokens stay until they expire, because detecting reuse needs them. Tokens of a user who never comes back stay until the user is deleted.

**Revocation:**
- Logout revokes the current refresh token and clears the cookie. Tokens of that user replaced a moment ago stop being accepted too.
- Password reset revokes all refresh tokens (also ending the 10 seconds above) and sets `users.credentials_changed_at`; access tokens issued before that are rejected straight away (`TokenValidityFilter`).
- Deleting a user deletes their refresh tokens (cascade); their access tokens are rejected straight away.

## Passwords

bcrypt (Quarkus `BcryptUtil`). At least 8 characters and at most 72 bytes in UTF-8: bcrypt ignores everything after byte 72, so a longer password is refused ("Password is too long") instead of being cut off. Plain ASCII passwords can be 72 characters; accented letters take two bytes and emoji four. Login does not check the length, so a longer password set before this rule keeps working. A failed login with an unknown username still runs a bcrypt comparison, so response time doesn't reveal whether an account exists; the message is always "Invalid credentials".

## Roles

- `USER`: own data only.
- `ADMIN`: user management (and later: all requests).

Backend: `@RolesAllowed` on resources, ownership checks in services. Frontend: `authGuard`, `adminGuard`, `guestGuard`, `setupDoneGuard`, `setupPageGuard` in `core/auth/guards.ts`. The frontend guards are for navigation only; the backend is the real check.

## Keys

In dev and test Quarkus generates a throwaway key pair on startup (tokens don't survive a restart; the refresh cookie does, so users are silently logged back in). In production set `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH`; see `local/README.md` for generating them.
