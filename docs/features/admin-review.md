# Admin review, status flow and final price

Issue: #5 (part of #2); the Printing status is #22.

## What it does

- **All requests** (admin only, *All requests* in the navbar, `/admin/requests`):
  - Lists everyone's requests, newest first, using the same table/cards as *My requests* plus a requester column.
  - The status filter defaults to **Open** (Submitted, In review, Accepted, Printing). It also offers **All** and each single status, and changing it reloads straight away.
  - The search box matches the request name or the requester's username, ignoring case. It runs on the server after a short typing pause.
  - No paging (prototype).
- **Status** (admin, the status badge next to the request name; #65, like the [item statuses](#item-statuses)): the badge is a dropdown with only the statuses the request may move to. Users, and the admin on a final request, see a plain badge.
  - In review, Accepted and Printing are applied immediately.
  - Declined and Completed are final, so they ask for confirmation first, in a row under the name. Declining takes an optional reason (max 2000), which the user sees on the detail page.
  - Completed is disabled ("set a price first") until a price is set.
  - Errors from the server (e.g. someone else changed it first) appear under the name.
- **Price & payment panel** (admin, on the request detail page, above the details; shown while the price or paid can still change):
  - **Final price** (in the [currency](pricing.md#currency), whose symbol is in front of the field): 0 to 99999999.99 with at most 2 decimals. *Save* (or Enter) sets or changes it; *Clear* returns it to "Price pending" (or the [estimate](pricing.md)). Locked while the request is paid.
  - While there is an [estimated price](pricing.md) (#72), it is the field's placeholder, and *Save* with an empty field saves it.
  - **Paid:** *Mark as paid* / *Mark as unpaid* on Accepted, Printing and Completed requests that have a price. This is the only thing that can still change on a Completed request.
  - The page updates in place after every change. Errors from the server appear in the panel.
- The back link on the detail page goes to *All requests* for the admin and *My requests* for users.
- Users see the price as soon as it is set (also while still in review), and the decline reason once declined.
- Paid requests show the price crossed out with a green **Paid** badge, for users and the admin, in lists and on the detail page.

## Prices

- **Display:** prices are in the [currency](pricing.md#currency) from Settings and formatted the way the viewer's browser formats numbers (#75): `€6,900.42` in an English (US) browser, `€ 6.900,42` in a Dutch one. Always 2 decimals. The rest of the UI, dates included, stays English.
- **Input:** the price field is a text field (`inputmode="decimal"` gives a number keyboard on phones). It accepts both styles:

  | Typed | Saved |
  |---|---|
  | `69,42` / `69.42` | 69.42 |
  | `69,4` | 69.40 |
  | `6.900,42` / `6,900.42` / `6 900,42` | 6900.42 |
  | `6.900` / `6,900` / `6900` | 6900 |
  | `1'234.50` / `1’234.50` | 1234.50 |
  | `$ 12,34` / `12,34 €` / `CHF 5` | 12.34 / 5 |
  | `69,4231`, `6.90.0`, `6.900.42`, `-5` | error |

- **Thousands vs. decimals:** a separator followed by exactly 3 digits is always a thousands separator, because prices have at most 2 decimals. So `69,423` means 69423, not 69.42.
- **After saving:** the field shows the saved price in the viewer's format, without thousands separators (`6900.42` or `6900,42`). The placeholder and error messages use the same format.

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

## Item statuses

Issue: #61. Every file and link of a request has its own status, so the admin can accept, decline, print and complete items one by one.

- **Statuses:** *Pending* (new items, also ones added later to an accepted request), *Accepted*, *Declined*, *Printing*, *Completed*, in the same colours as the request statuses.
- **On each card** (Model card and link card), in the header: a status badge for everyone. For the admin it is a dropdown (ng-bootstrap) with the allowed next statuses; choosing one saves it right away.
- **Declining an item** asks for confirmation with an optional reason (max 500). The reason shows in the card for the requester too: "Declined: <reason>". Any other status drops the reason.
- **Flow** (`ItemStatus.nextStatuses()`, sent per item as `nextStatuses`):

  | From | Allowed to |
  |---|---|
  | Pending | Accepted, Declined |
  | Accepted | Printing, Completed, Declined |
  | Printing | Completed, Accepted (failed print), Declined |
  | Declined, Completed | Accepted (reopened) |

  Items only change while the request isn't final; on a Declined or Completed request they are locked (`409`, no dropdown).
- **The request moves its items along** (in the same transaction):
  - accepting it (from Submitted or In review) accepts its Pending items;
  - declining it declines every item that isn't Completed (no per-item reason; the request's reason covers them);
  - completing it completes every item that isn't Declined, Pending included. Nothing blocks completing.
  - Moving to Printing, back to Accepted or to In review leaves the items alone. Items never change the request's status.
- **Declined items** count nowhere but in the numbers of files, links and declined items: not in the copies, filament or print time.
- **Progress:** the Files heading adds "· 1 declined" and "· 2/3 completed"; the request lists show "2/3 completed" next to the status badge. Progress counts the items that aren't declined, and only shows from Accepted onwards and with more than one such item.
- Owner rules (remove, quantity while Submitted), downloads and *Re-slice with…* work for items in any status.
- Data: `request_files.status` and `decline_reason` (V12). Existing items got a status from their request: Submitted/In review → Pending, Accepted/Printing → Accepted, Completed → Completed, Declined → Declined.

## Not included

Comments, editing request details, notifications (also when printing starts), per-status timestamps, paging. The estimated price is in [pricing](pricing.md) (#72). The filaments requesters chose show on the model and link cards ([Spoolman filaments](spoolman.md), #11). Out of #61: per-item prices, items changing the request status, the requester changing item statuses.
