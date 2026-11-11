# PrintBridge – working agreement

Web app where registered users request custom 3D prints (STL/3MF), pick filament from a self-hosted Spoolman, and the owner reviews/prices/accepts. See README.md for the full goal and `docs/` for what exists.

Stack: Quarkus + Panache + PostgreSQL + Flyway (`PrintBridge-Backend`), Angular 22 + Bootstrap (`PrintBridge-Frontend`), local deps in `local/` (docker compose), config via env vars (`.env.example`).

## Architecture

### Frontend
- Layering: `component.html` → `component.ts` → `service.ts` → backend API.
  - The template only uses what its component exposes.
  - Components never call HTTP directly; services do.
- Angular defaults: standalone components, signals, `inject()`, lazy-loaded routes, auth interceptor + route guards.
- UI: Bootstrap. Functional and tidy, not polished.
- Add `data-cy="..."` attributes to interactive elements/key content (Cypress convention). Cypress tests are only written when asked.

### Backend
- Controller (REST) → Service → Panache/DB.
  - Controllers: HTTP in/out, status codes, `@RolesAllowed`, input validation (Bean Validation on DTOs). No business logic.
  - Services: business logic, ownership checks (users only see their own requests), querying and filtering.
- Never return entities from controllers: use DTOs (records) with mappers.
- Mappers/utils are fine when something is reused.
- Spoolman is read-only and backend-only (REST client + cache). Pricing goes through `PricingStrategy`.
- Schema changes only via Flyway migrations (`db/migration`); Hibernate never alters the schema.
- Config only through env vars / `application.properties`; no hardcoded secrets or URLs.

## Testing
- Target 80–100% unit-test coverage on controllers and services, frontend and backend. It is a target (report it), not a CI gate.
- Run the relevant tests before reporting something as done.
- No Cypress/E2E tests unless asked.

## Communication & autonomy
- Work autonomously: write code, run builds/tests, edit files, install well-known dependencies.
  - Obscure or unpopular dependencies: ask first.
- Ask before: commit, push, opening a PR (each time); using a browser (never without being asked or told to – if something needs browser testing, hand it back to the user).
- Unsure or something questionable? Report and ask instead of guessing.
- Unrelated bug found while working: don't fix it silently. Tell the user how big and how critical it is, ask if it should be fixed here, and whether it deserves its own issue.
- No `Co-Authored-By` lines in commits.
- Feature requests: always discuss first (see `/request-feature`). Bugs: `/report-bug`, no interview.

## Issues, board & documentation
- Every piece of work has a GitHub issue first (feature template; chores use it too). Tiny things (typos etc.) can skip it.
- Bugs use the bug template and don't need the interview.
- Keep the issue up to date while working and after finishing.
- Project board "PrintBridge - SCRUM" collects issues automatically. Moving cards (Backlog → Sprint → In progress → ...) is allowed but the user may do it too.
- `docs/`: markdown per feature (what exists and how it behaves) plus standing docs (architecture, API, auth). Update with each feature.
- Javadoc/comments where useful (the why, not the what).

## Branching & PRs
- `main`: always working. Only via PR; pipeline must pass.
- `dev`: collects finished features, also where last fixes land if something breaks. Only via PR; pipeline must pass.
- `feature/<issue-number>-<short-name>` (bugs: `fix/<issue-number>-<short-name>`): branch from `dev`, PR into `dev` when done.
- Urgent fixes may go via a PR straight to `main` (then bring `dev` back in line).
- Merging `dev` into `main` is done by the user.
- PR bodies contain `Closes #N`.
- Review: the user reviews and comments "approved" (GitHub doesn't allow approving your own PR); the user merges.

## Definition of done
Merged, pipeline green, user reviewed and approved. Issue and docs updated.
