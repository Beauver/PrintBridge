# First-run setup, login and user management

Issue: #3 (part of #2).

## What it does

- **First run:** with no admin in the database, every page redirects to *Welcome to PrintBridge*, where the owner creates the admin account (username, email, password twice). They're logged in straight away. The page can't be used again afterwards.
- **Login:** username or email plus password. Wrong details always show "Invalid credentials". After too many attempts (10 logins a minute from one address, or 5 wrong passwords for one account in 15 minutes) the page shows "Too many attempts, try again in N seconds". Users stay logged in across reloads for up to 14 days of inactivity (refresh token); see [auth](../auth.md). Opening several tabs at once keeps the user logged in in all of them.
- **Logout:** navbar button; ends the session on the server too.
- **Users page (admin):** *Users* in the navbar.
  - *New user*: username, email, password (twice). Duplicate username/email shows an error.
  - Passwords (setup, new user, update) need at least 8 characters and may take at most 72 bytes; a longer one shows "Password is too long". That is 72 plain characters, fewer with accents or emoji.
  - *Update user* (#28): a dialog with username and email filled in and empty *New password* / *Confirm new password* fields; leaving them empty keeps the password. Same rules as *New user*; a username or email of another account shows an error in the dialog. A new password logs that user out everywhere; changing only the username or email keeps them logged in. On the admin's own row the dialog also asks for the current password and works like *My account*.
  - *Delete*: asks for confirmation; the user is logged out everywhere. Their print requests and files are deleted too. The admin can't delete their own account.
- **My account** (#28): clicking your username in the navbar opens `/account`, with your username and email filled in. You can change them and set a new password (typed twice; empty keeps the current one). Saving always needs your current password; a wrong one shows "Current password is incorrect". A new username shows in the navbar right away. A new password logs out all your other sessions, but this browser stays logged in.
- **Theme:** dark by default; the round button in the bottom-right corner (on every page, also login/setup) switches to light and is remembered in the browser.
- **Start page:** the user's print requests, see [print requests](print-requests.md).

## Not included

Self-registration, password-reset emails, avatars, changing roles.
