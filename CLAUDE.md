# Rules for working on AARE Patches

Talk to the owner in **Italian**.

## Before proposing a patch

- **Check that AARE doesn't already do it.** Read AARE's README and sources (github.com/prototux/AARE) and the
  navigation/messaging apps' own features. Don't duplicate: only improve or extend what AARE has (for example,
  doing by voice while driving what AARE only allows by touch when parked).
- Changes must work for every car, not only the owner's Skoda.

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

## Before publishing

- Work on a branch and open a pull request; merge to `main` (which releases) only when CI is green:
  the patches build, apply to the official AARE APK, tests pass, security checks pass.
- Review every change against the security list above.
- `feat:` for new features, `fix:` for fixes, `chore:` for anything that must not release.
- Update the README (patch description and "What's new / Novità") with every feature.
- Things CI can't test (car audio, microphone, tunnels) are tested by the owner in the car before being
  recommended to others.
