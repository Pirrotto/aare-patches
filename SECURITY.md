# Security policy

## Reporting a vulnerability

Please **don't open a public issue** for a security problem. Use GitHub's private report instead:
the **Security** tab of this repository → **Report a vulnerability**. Only the maintainer sees it.

Say what you found, how to reproduce it, the AARE version and the patches' version (shown at the
bottom of the patches' Settings, and as `patches=` in "Copy diagnostics"). Don't paste personal
data: the diagnostics hold technical state only, and that is enough.

You'll get an answer as soon as possible; a fix comes in a test build first, then in the next
release.

## Supported versions

Only the latest release of the patches, for the AARE version it names. Older releases stay on
GitHub so you can go back, but they get no fixes.

## What the patches do and don't do

- **No network:** the patches open no internet connection and contain no analytics, crash
  reporting, ads or tracking libraries.
- **No new permission:** the patches add no Android permission to AARE. Voice messages are read
  through Shizuku, which AARE already uses, limited to `PTT-` files in WhatsApp's received
  voice-note folders, with canonical paths checked.
- **No exported component:** the patches' Settings screen is not exported.
- **Personal data:** messages, dictated text, contacts and locations are never written to logs,
  files or the clipboard. A conversation kept to answer from the notification centre stays in
  memory only, for at most one hour, and is wiped when the car disconnects.
- **Diagnostics:** technical state only, in AARE's private storage (excluded from backup), newest
  1000 lines; copied or deleted only when you tap "Copy diagnostics" or "Delete diagnostics".
- **Confirmation:** a message is sent only after a clear spoken "yes" or the Send button; with
  silence or an error nothing is sent.
- **Only official AARE:** the patches apply only to official AARE releases (signature check).
- **Builds:** releases are built by GitHub Actions from the tagged source in this repository.
