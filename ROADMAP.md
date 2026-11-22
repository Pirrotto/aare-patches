# Roadmap

Every item is checked against AARE first (see CLAUDE.md): only what AARE doesn't do, or does only when parked.

## Next update

- [ ] Fixes from the car tests (tunnel navigation, car audio and voice notes over integrated Shizuku, assistant,
      car-microphone dictation)
- [ ] Integrated Shizuku log readable by shell only
- [ ] Checks before every release: branch + pull request, CI builds the patches, applies them to the official
      AARE APK, runs the tests and the security checks; merge to `main` only when green
- [ ] Translations of every patch text, chosen by the system language (English when missing): Spanish,
      French, Portuguese, German (complete), Dutch, Polish, Chinese, Japanese, Russian, Turkish, Arabic
- [ ] Spoken yes/no (send/cancel) in the same languages

## Later

- [ ] Smarter AARE assistant with Dicio's ideas (reference-sentence matching, dates and numbers), in more languages
- [ ] Weather and timers by voice in the assistant
- [ ] Nicknames for contacts in the assistant ("mamma", "amore")
- [ ] "Where did I park?" (position saved when the car disconnects)
- [ ] Telegram / Nekogram voice messages (check where they are stored first)
- [ ] Mixed Italian + German recognition (German street names)
- [ ] "Take me home" passed to the navigation app (check that Vela accepts it)
- [ ] Fuel and tyre alerts from the car data
- [ ] Backup and restore of the patch settings
- [ ] Tested-cars list in the README

## Outside the code

- [ ] Discussion on prototux/AARE proposing the features upstream
- [ ] Reply to mgitp on prototux/AARE#324
- [ ] Car report for the Skoda Octavia 2022 (MIB3)
