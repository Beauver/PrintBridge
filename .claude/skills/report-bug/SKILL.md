---
name: report-bug
description: Report and fix a bug in PrintBridge. Writes a GitHub issue from the bug template with no long interview, investigates, fixes with a regression test, and opens a PR. Use when the user runs /report-bug or describes something that is broken.
argument-hint: <what is going wrong>
---

# /report-bug

Quick path for bugs: no feature interview. Follow `CLAUDE.md` for architecture, testing, and git rules.

The bug: $ARGUMENTS

## 1. Triage
- Reproduce or locate the cause in the code (and logs/tests) before writing anything. Don't use a browser unless the user asked for it; if the bug needs one to reproduce, hand that step back.
- Only ask questions that block reproduction (steps, expected vs actual, environment). Keep it to one short batch.
- Assess and tell the user: how big (affected area) and how critical (data loss, security, blocks users, cosmetic), and the likely cause if known.

## 2. Create the issue
- Use `.github/ISSUE_TEMPLATE/bug_report.md` structure: Describe the bug / To Reproduce / Expected behavior / Additional context (suspected cause, severity). Skip the desktop/smartphone sections when they don't apply.
- Label `bug`. Create with `gh issue create`; the project board picks it up automatically.
- Show the user the issue and ask whether to go ahead with the fix. Skip the issue only for trivial things (typos, etc.).

## 3. Fix
- Branch `fix/<issue-number>-<short-name>` from `dev`. For something urgent the user flagged, branch from `main` and target `main` instead.
- Write a failing test that reproduces the bug first where practical, then fix it. Keep the change minimal: no refactors or scope creep. Unrelated problems are reported, not fixed.
- Run backend and frontend tests and the build until green. Update docs only if documented behaviour changed.

## 4. Hand over
- Report: root cause, the fix, tests added, anything notable.
- Ask permission to commit, push and open the PR. When granted: short imperative commit message, no `Co-Authored-By`, PR targets `dev` (or `main` if urgent), body includes `Closes #N`. For an urgent fix to `main`, remind the user that `dev` needs to be brought back in line.
- Update the issue with the cause, fix and PR link.

## 5. Review loop
- Apply the user's review feedback on the same branch (ask before each push). The user merges. Done = merged, green pipeline, approved.
