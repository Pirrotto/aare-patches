# Rules for working on AARE Patches

Talk to the owner in **Italian**.

## Before proposing a patch

- **Check that AARE doesn't already do it.** Read AARE's README and sources (github.com/prototux/AARE) and the
  navigation/messaging apps' own features. Don't duplicate: only improve or extend what AARE has (for example,
  doing by voice while driving what AARE only allows by touch when parked).
- Changes must work for every car, not only the owner's Skoda.

## What AARE's maintainer accepts (prototux, Discussion #339)

The patches follow these too, since they must match what is proposed to AARE.

- **Every proposal to AARE:** one pull request per feature, off by default, with a short entry in
  `docs/decisions.md`. No need to translate: AARE does a translation pass before each release.
- **Tunnel navigation:** proposed only after the owner's road test, as an opt-in. Other navigation apps
  receive this position, so the estimate stops after a time or distance limit and always reports an honest
  accuracy (never lowered to get an app to accept it). The maths is covered by unit tests.
- **Car microphone for dictation:** hangs off AARE's existing setting from #256 (car microphone for phone
  apps), not a new switch.
- **Voice reply:** opt-in at first, working alongside AARE's existing message reply.
- **WhatsApp voice notes:** maybe, as an opt-in with Shizuku, limited to received voice notes; the maintainer
  reviews that part carefully.
- **Not to propose:** an assistant inside AARE (it belongs in a separate app using the car tools AARE
  exposes) and Shizuku integrated into AARE (the Shizuku app already does it).

## Security and privacy (never relax these)

- No internet connections added by the patches.
- No personal data (messages, dictated text, contacts, location, voice notes) in logs, the clipboard, or files
  other apps can read. Diagnostics hold technical state only.
- No new permission without a stated reason; prefer Shizuku over broad permissions (never all-files access).
- No exported components unless required, and then protected.
- File access limited to the minimum (voice notes: only `PTT-` files in WhatsApp's received voice-note folders,
  canonical paths checked).
- The integrated Shizuku server serves AARE's uid only; wireless debugging is switched off after the start.
- Sending a message or placing a call always needs the user's confirmation.
- Patches accept only official AARE releases (signature check).

## Never break what works

- Every change must keep the existing features working exactly as before, unless the owner asked to change them.
- Small steps: one feature or fix per pull request, touching only the code it needs.
- New behaviour is optional (its own switch or setting) and the old behaviour stays the default until tested.
- Every fix and feature gets automatic tests where possible; the whole suite runs on every pull request
  (regression tests), and all patches are applied together to the official APK.
- Before merging, list in the pull request what could be affected and how it was checked.
- After a release, the owner tests the existing features in the car too, not only the new ones.
- Previous releases stay on GitHub, so the owner can go back to the last working version at any time.

## How to work on code (every change)

- **Understand, design, write, verify.** Never code on guesses: look in the code; if it isn't there, ask.
- **Before touching code:** read what exists and reuse or extend it instead of duplicating; list every place
  that uses what you change (callers, references, settings, tests); follow the project's conventions; say in
  2–3 lines what the code does today and what it will do after; use only APIs and libraries of the versions
  the project uses.
- **Design first:** for any change that isn't trivial, show the owner the plan (files, approach, risks,
  alternatives left out and why) and wait for his OK. Weigh at least two approaches and take the simplest
  robust one; no over-engineering. Think of edge cases (empty, null, malformed, extremes, concurrency, I/O
  errors, permissions, first run, update from an older version) and of the user (clear messages, no silent
  failure, no surprise). Backward compatible by default; a break is stated, explained and migrated.
- **Writing:** minimal, targeted changes; no drive-by refactoring or reformatting. Don't remove or alter
  working code without understanding why it's there. Complete code: no TODOs, placeholders or empty
  functions. Clear names; comments for the why, not the what. Handle errors explicitly, never swallow them
  silently. No hard-coded values that should be settings. No secrets, validate external input, least
  privilege. No dead code, forgotten debugging or noisy logs. Same style as the project.
- **Verify before delivering:** reread the whole diff as a strict outside reviewer; it must build, start and
  pass the checks (here CI builds the Android code: say so when a check fails, and never call something done
  before it's green); test the new behaviour and its edge cases; check that what was touched, directly or
  not, still works; recheck every place listed above. Say plainly what could not be verified (car audio,
  microphone, tunnels, other Android versions): never say "it works" without having tried it.
- **Delivering:** every pull request says what changes, why, the files, what could be affected and how it
  was checked, the risks and how to test it. Raise doubts instead of hiding them. Bugs found outside the task
  are reported separately, not fixed on the side. One pull request, one goal. Be honest about compromises.

Checklist before every delivery: existing code read and reused · every affected place listed · simplest
robust approach, alternatives weighed · edge cases and errors handled · project style · no TODO,
placeholder, dead code or secret · builds and passes the checks · new behaviour tested · nothing existing
broken · backward compatible (or the break stated) · clear description with risks and tests · what wasn't
verified said plainly.

## Privacy and security in every change (OWASP MASVS, OpenSSF, F-Droid, Android guidelines)

- **Least data:** use only the data a feature needs; ask only for the permissions it needs, when it needs
  them, saying why. Location is never stored in history or logs and never leaves the phone; background
  location only while a car is connected or a navigation runs. Microphone open only while needed, closed
  right after, always with a visible or audible sign. No persistent user or device identifiers.
- **No tracking:** no analytics, crash reporting, ads or third-party tracking libraries; no undeclared network
  connection (any connection documented and switchable off); no downloaded executable code without explicit,
  optional consent.
- **Logs and diagnostics:** never message text or audio, contact names or numbers, coordinates, passwords or
  tokens; network identifiers (MAC, BSSID, SSID) shortened or anonymised; logs stay on the phone and are shared
  only by an explicit action of the user, who sees what is shared; automatic rotation by size or age; detailed
  protocol logs off by default; an export or copy keeps the newest lines.
- **Storage:** sensitive data only in the app's private storage, never shared storage; keys and certificates in
  the Android Keystore; sensitive data excluded from backup; caches of messages (such as reply actions) expire
  soon and are emptied when the car session ends.
- **Network and crypto:** TLS only (cleartext forbidden by network security config); never disable certificate
  checks; only the platform's standard cryptography; verify the head unit's identity and warn if it changes.
- **System:** components not exported unless needed, and then protected by a permission; every intent or data
  from other apps or the car is untrusted and validated; Shizuku and elevated privileges only for what truly
  needs them, least privilege, never exposed to other apps; no action with external effects without explicit
  confirmation (with silence or an error nothing is sent).
- **Code and dependencies:** before adding a dependency, ask whether it can be avoided; then check it is
  maintained, trustworthy, correctly named; prefer open source; update vulnerable ones quickly. No secrets in the
  repository, with an automatic check. Automatic tests including negative ones (the wrong thing does NOT
  happen). Linter and static analysis in CI; release builds without debug. Every change reviewed before merging.
- **Transparency:** tell the user plainly which data is used and why; the user can see, export and delete their
  data (logs, settings); reproducible builds and signed releases; a SECURITY.md on how to report
  vulnerabilities; the published code matches the distributed binary exactly.

Privacy checklist before every delivery: no new permission or data without real need · background location
only with consent and only during the session · no tracking, analytics or undeclared connection · no messages,
contacts, positions or identifiers in clear in logs · sensitive data and keys stored safely and out of backup ·
no external action without explicit confirmation · components not exported, external input validated · new
dependencies weighed and needed.

## Branches and releases

- Day-to-day work: a branch per change, pull request into `dev`, merged when CI is green (unit
  tests, build, patches applied to the official AARE APK with `--validate`, none skipped).
- Every push to `dev` keeps the patches file as a private test build (artifact of the "Test build"
  run). Send the owner the run's link; he loads the file in Morphe (Sources → + → select file).
- Public releases only from `main`, at most twice a week: Thursday evening, Saturday evening or
  Sunday morning, and only after the owner tested in the car and said yes. One pull request
  `dev` → `main` per release; one release with everything since the last.
- Never push to `main` directly. Don't create releases, tags or extra branches the owner didn't ask
  for; delete work branches after merging.
- Patches proposed to AARE are the same code and behaviour as the pull request to AARE, so the
  owner's car test counts as the road test.

## Before publishing

- Work on a branch and open a pull request; merge only when CI is green.
- Review every change against the security list above.
- `feat:` for new features, `fix:` for fixes, `chore:` for anything that must not release.
- Never put a link to the Claude session (claude.ai/code/session_…) in commits, pull requests,
  comments or releases, in this repo or in AARE.
- With every change to what a patch does, update all three descriptions together: the patch's summary in the
  patches' Settings (`PatchSettings`), its Morphe description (`description` in the patch file, shown in Morphe and
  in `patches-list.json`), and the README (patch description and "What's new / Novità").
- Things CI can't test (car audio, microphone, tunnels) are tested by the owner in the car before being
  recommended to others.
