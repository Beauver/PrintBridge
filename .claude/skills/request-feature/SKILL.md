---
name: request-feature
description: Start a new feature (or chore) for PrintBridge. Interviews the user about the request, writes a GitHub issue from the feature template, waits for approval, then implements it and opens a PR. Use when the user runs /request-feature or asks to add/change functionality.
argument-hint: <short description of the feature>
---

# /request-feature

Turn a rough request into a precise GitHub issue, then implement it. Follow `CLAUDE.md` for architecture, testing, and git rules. Never skip a gate: each step ends with the user's go-ahead.

The user's request: $ARGUMENTS

## 1. Discussion (no files, no issue yet)
- Look at the relevant code and `docs/` first so your questions are informed.
- Interview the user about the request:
  - Goal and the problem it solves; who uses it (requester, owner, or both).
  - Exact behaviour, UI flow, and what the user sees at each step.
  - Edge cases, errors, empty states, permissions (USER vs ADMIN).
  - Data: new fields/entities, migrations, effects on existing data.
  - Interaction with existing features (Spoolman, pricing, uploads, comments, status flow).
  - Scope: what is explicitly out of this feature.
- Ask in small batches (use the question tool for choices, plain text for open questions). Offer a recommendation for each choice. Don't ask what the code or docs already answer.
- Repeat until neither of you has open questions. Summarise what was agreed and ask: "Happy to write the issue?"

## 2. Create the issue
- Use `.github/ISSUE_TEMPLATE/feature-request.md` structure (chores too). Fill every section:
  - What / Solution / Existing functionality / Additional context
  - Developer part: Scope (in/out), Designs (or a clear description), Acceptance Criteria (each action → expected result, testable)
- Add the `Feature` label. Create it with `gh issue create`; the project board picks it up automatically.
- Show the user the issue (number, link and the text). Ask for feedback and approve/decline.
- Feedback → edit the issue and ask again. Do not start implementing before explicit approval.

## 3. Implement (after approval)
- Create `feature/<issue-number>-<short-name>` from the latest `dev`.
- Optionally move the card to In progress (the user may do it too).
- Build it per `CLAUDE.md`: controller → service → Panache, DTOs + mappers, Flyway migration for schema changes, component → service in Angular with Bootstrap and `data-cy` attributes.
- Write unit tests for new controllers/services (aim for 80–100%); run backend and frontend tests and the build until green.
- Add/update `docs/<feature>.md` and the standing docs (architecture/API/auth) if affected; add Javadoc/comments where the why isn't obvious.
- Anything sketchy, unclear, or out of scope: stop and ask; don't silently expand scope.
- If the work deviates from the issue, update the issue.

## 4. Hand over
- Report: what was built, test results and coverage, anything notable or questionable, any unrelated bugs spotted.
- Ask permission to commit, push, and open the PR. When granted:
  - Commit messages short and imperative, no `Co-Authored-By`.
  - PR targets `dev`, body summarises the change and includes `Closes #N`.
- Update the issue (final behaviour, notes, link to the PR) and optionally move the card to In review.

## 5. Review loop
- The user reviews and comments feedback or "approved". Apply requested changes on the same branch (ask before each push), keep tests green, update the issue/docs.
- The user merges. Done = merged, green pipeline, approved.
