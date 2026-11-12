# First-run setup, login and user management

Issue: #3 (part of #2).

## What it does

- **First run:** with no admin in the database, every page redirects to *Welcome to PrintBridge*, where the owner creates the admin account (username, email, password twice). They're logged in straight away. The page can't be used again afterwards.
- **Login:** username or email plus password. Wrong details always show "Invalid credentials". Users stay logged in across reloads for up to 14 days of inactivity (refresh token); see [auth](../auth.md).
- **Logout:** navbar button; ends the session on the server too.
- **Users page (admin):** *Users* in the navbar.
  - *New user*: username, email, password (twice). Duplicate username/email shows an error.
  - *Reset password*: sets a new password and logs that user out everywhere.
  - *Delete*: asks for confirmation; the user is logged out everywhere. Their print requests and files are deleted too. The admin can't delete their own account.
- **Theme:** dark by default; the round button in the bottom-right corner (on every page, also login/setup) switches to light and is remembered in the browser.
- **Start page:** the user's print requests, see [print requests](print-requests.md).

## Not included

Self-registration, changing your own password, password-reset emails, rate limiting on login.
