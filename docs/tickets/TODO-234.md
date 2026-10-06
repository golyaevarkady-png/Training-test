# TODO-234: Dark flash on load when the saved theme is light

**Type:** Bug
**Area:** Frontend
**Priority:** Low

## Found by

`/code-review` of TODO-231. Left out of TODO-231 on purpose; reason below.

## Problem

`index.html` ships `<html data-theme="dark">` and the button label "Light theme",
because dark is the default (TODO-231, AC-4). `app.js` only applies a saved theme when
it runs, at the end of `<body>` on `DOMContentLoaded`. A user who saved `light` therefore
sees the page paint dark first and then switch to light, on every load. The dashboard
stays open on a wall screen and second monitors, so every reload flashes.

## Why it was not fixed in TODO-231

- The fix needs a small inline script in `<head>` that reads `localStorage['ops-theme']`
  and sets `data-theme` before first paint. That duplicates the theme-reading logic
  that lives in `initApp` (key name, valid values, the dark default), so it needs a
  decision on where the single source of truth lives.
- The Jest harness (`src/test/javascript/setup/loadApp.js`) mounts only the `<body>` of
  `index.html`, so a `<head>` script would be untested unless the harness changes too.
  That is a harness change, bigger than the TODO-231 fences invited.
- Users on the default theme (dark) do not see the flash; only users who chose light do.

## Acceptance criteria

- **AC-1** With `ops-theme=light` stored, the first paint is already light (no dark
  frame). Checked in a real browser (Playwright), not only jsdom.
- **AC-2** The storage key, the valid values and the dark default are defined in one
  place, shared by the `<head>` script and `app.js`.
- **AC-3** The harness can run the `<head>` script, and a Jest test covers it.

Fences: both suites green; frontend only; no new dependencies.
