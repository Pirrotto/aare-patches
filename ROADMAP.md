# Roadmap

Checked against AARE first (see CLAUDE.md): only what AARE doesn't do yet.

## Testing (owner, in the car)

- [ ] Patches for AARE 1.6.0 (test build from `dev`): tunnel navigation, voice messages, voice reply
- [ ] AARE test build with Reply by voice (prototux/AARE#353)

## Proposed to AARE

- [x] Background location – in AARE 1.6.0 (#351)
- [ ] Reply by voice – #353, for 1.6.1; road test with a build before it ships
- [ ] Tunnel navigation – after the road test; same code as the patch, with its tests
- [ ] Voice messages – after the road test; opt-in with Shizuku, received voice notes only

## Proposed to Shizuku

- [ ] Official start on boot via wireless debugging, and a "start request" for authorized apps
      (RikkaApps/Shizuku discussion) – waiting for an answer

## Later

- [ ] Translations of the patches' texts (system language, English when missing)
- [ ] Requirements per patch in the Settings screen
- [ ] "Where did I park?"
- [ ] Telegram / Nekogram voice messages
- [ ] Backup of the patch settings in AARE's settings export
- [ ] Tested-cars list in the README, from the "Car test" reports

## Not planned

- AARE assistant and Integrated Shizuku in AARE (its author prefers separate apps and the Shizuku app)
- New internet connections from the patches
