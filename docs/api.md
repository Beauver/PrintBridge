# API

All endpoints are under `/api`. Errors handled by the app return `{"message": "..."}`; validation errors return Quarkus' format with a `violations` list (`field`, `message`).

## Setup

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| GET | `/setup` | public | – | `200 {setupRequired}` |
| POST | `/setup` | public, only while no admin exists | `{username, email, password}` | `201` auth response + refresh cookie; `409` if already done; `400` invalid input |

## Auth

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| POST | `/auth/login` | public | `{login, password}` (`login` = username or email, case-insensitive) | `200` auth response + refresh cookie; `401 Invalid credentials` |
| POST | `/auth/refresh` | refresh cookie | – | `200` auth response + new cookie; `401` |
| POST | `/auth/logout` | public | – | `204`, cookie cleared |
| GET | `/auth/me` | logged in | – | `200` user |

Auth response: `{accessToken, expiresIn (seconds), user}`.

## Users (admin only)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/users` | – | `200` list of users, sorted by username |
| POST | `/users` | `{username, email, password}` | `201` user; `409` username/email taken; `400` invalid |
| PUT | `/users/{id}/password` | `{password}` | `204`; `404` unknown user. Logs the user out everywhere |
| DELETE | `/users/{id}` | – | `204`; `404` unknown; `409` deleting yourself |

User: `{id, username, email, role, createdAt}`.

Validation: username 3–32 characters (`A-Z a-z 0-9 . _ -`), unique ignoring case; email valid, max 254, unique ignoring case; password 8–72 characters.

Non-admins get `403`, requests without a valid token `401`.
