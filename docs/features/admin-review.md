# Admin review, status flow and final price

Issue: #5 (part of #2); the Printing status is #22.

## What it does

- **All requests** (admin only, *All requests* in the navbar, `/admin/requests`):
  - Lists everyone's requests, newest first, using the same table/cards as *My requests* plus a requester column.
  - The status filter defaults to **Open** (Submitted, In review, Accepted, Printing). It also offers **All** and each single status, and changing it reloads straight away.
  - The search box matches the request name or the requester's username, ignoring case. It runs on the server after a short typing pause.
  - No paging (prototype).
- **Review panel** (admin, on the request detail page, above the details; hidden once the request is final):
  - **Status:** a Jira-style dropdown showing the current status and only the statuses it may move to.
    - In review, Accepted and Printing are applied immediately.
    - Declined and Completed are final, so they ask for confirmation first. Declining takes an optional reason (max 2000), which the user sees on the detail page.
    - Completed is disabled until a price is set.
  - **Final price (EUR):** €0 to € 99.999.999,99 with at most 2 decimals. *Save* (or Enter) sets or changes it; *Clear* returns it to "Price pending". Locked while the request is paid.
  - **Paid:** *Mark as paid* / *Mark as unpaid* on Accepted, Printing and Completed requests that have a price. This is the only thing that can still change on a Completed request.
  - The page updates in place after every change. Errors from the server (e.g. someone else changed it first) appear in the panel, and the dropdown resets.
- The back link on the detail page goes to *All requests* for the admin and *My requests* for users.
- Users see the price as soon as it is set (also while still in review), and the decline reason once declined.
- Paid requests show the price crossed out with a green **Paid** badge, for users and the admin, in lists and on the detail page.

## Prices

- **Display:** prices are shown the Dutch way everywhere: `€ 6.900,42`. The rest of the UI, dates included, stays English.
- **Input:** the price field is a text field (`inputmode="decimal"` gives a number keyboard on phones). It accepts both styles:

  | Typed | Saved |
  |---|---|
  | `69,42` / `69.42` | 69.42 |
  | `69,4` | 69.40 |
  | `6.900,42` / `6,900.42` / `6 900,42` | 6900.42 |
  | `6.900` / `6,900` / `6900` | 6900 |
  | `69,4231`, `6.90.0`, `6.900.42`, `-5` | error |

- **Thousands vs. decimals:** a separator followed by exactly 3 digits is always a thousands separator, because prices have at most 2 decimals. So `69,423` means 69423, not 69.42.
- **After saving:** the field shows the saved price in Dutch format (`6.900,42`).

## Status flow

| From | Allowed to |
|---|---|
| Submitted | In review, Accepted, Declined |
| In review | Accepted, Declined |
| Accepted | Printing, Completed (needs a final price) |
| Printing | Completed (needs a final price), Accepted (e.g. a failed print to queue again), Declined (can't be printed after all) |
| Declined, Completed | – (final) |

**Printing** (#22) tells the requester their print is on the printer. It shows as a yellow badge. Skipping it (Accepted → Completed) stays possible. While Printing, the price, paid and server re-slicing still work like for Accepted.

- **Final requests:** status and price can't change any more (`409`). Completed requests can still be marked (un)paid. The admin can still delete final requests, as in #4.
- **Owners:** can only delete while the request is Submitted (#4).
- **Single source of truth:** the allowed moves live in `RequestStatus.nextStatuses()`. The API sends them along as `nextStatuses`, so the frontend never duplicates the rules (except the "Completed needs a price" hint).

## Not included

Comments, editing request details, notifications (also when printing starts), per-status timestamps, Spoolman/price estimation, paging.
