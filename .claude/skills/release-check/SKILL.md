---
name: release-check
description: Run before opening a PR. Verifies both test suites against the baseline and the rest of the Lab 1 definition of done (element ids registered, pom.xml untouched, diff stat).
---

# Release check

Invoke with `/release-check`. It only reports; it changes nothing.

## Preconditions

- You are at the root of the ops-dashboard repository (`pom.xml` and `package.json`
  are both present).
- `node_modules/` exists. If not, run `npm ci` first and say so.
- The working tree has the change you intend to ship. Do not stash or discard
  anything.

## Steps

1. Run `./mvnw test` (without `-q`, so the summary prints). Capture the last line
   that starts with `Tests run:`: the overall summary after all the per-class lines.
   Note the exit code.
2. Run `npm test`. Capture the line that starts with `Tests:`. Note the exit code.
3. Compare both counts with the baseline below.
4. Check that every `id="..."` in `src/main/resources/static/index.html` is listed in
   `REGISTERED_IDS` in `src/test/javascript/setup/loadApp.js`, and the other way round.
   (`harness.test.js` enforces this too; report it separately so the reason is visible.)
5. Check that `pom.xml` has no changes against `origin/main`
   (`git diff --quiet origin/main -- pom.xml`). Dependencies are frozen; any change
   needs a CHG ticket.
6. Run `git diff --stat origin/main...HEAD` plus `git status --short` for uncommitted work.
7. Report using the output format.

## Constraints

- Baseline: **Java 25**, **Jest 60** (the count after TODO-231 on this repository).
- Compare the baseline with the number that **passed**, not the total: Jest and
  Maven both count skipped tests in the total (`1 skipped, 59 passed, 60 total`).
- A count BELOW the baseline is a **FAIL**, even if every test that ran passed.
  Fewer tests means something was deleted or skipped; find out what before
  reporting (`git diff origin/main -- src/test` and look for `.skip`, `xit`,
  `@Disabled`).
- Any skipped test (Jest `skipped`, Maven `Skipped:`) is a **FAIL**.
- Any failure or error, or a non-zero exit code, is a **FAIL**.
- A count above the baseline is fine when the change adds tests. In that case,
  raise the baseline in this file as part of the same change so the next run
  checks against the new number.
- An unregistered id or a changed `pom.xml` makes the verdict NOT READY.
- Never edit, skip or delete a test to make the check pass.
- Do not open the PR from this skill; it only reports.

## Output format

```
Release check
  Java:  <n> run, <f> failures, <e> errors, <s> skipped   (baseline 25)  PASS|FAIL
  Jest:  <n> passed, <s> skipped, <t> total              (baseline 60)  PASS|FAIL
  Ids:   registered | <unregistered ids>                    PASS|FAIL
  pom.xml: unchanged | changed                              PASS|FAIL
  Verdict: READY (baseline held) | NOT READY
  Notes: <one line per problem, or "none">

<git diff --stat output>
```
