# Roadmap

Every item is checked against AARE first (see CLAUDE.md): only what AARE doesn't do, or does only when parked.
Each version contains everything before it; the assistant items improve the existing "AARE assistant" patch.

## v1.11 – Phase A

- [ ] Fixes from the car tests (tunnel navigation, car audio and voice notes over integrated Shizuku, assistant,
      car-microphone dictation)
- [ ] Integrated Shizuku log readable by shell only
- [ ] Checks before every release: branch + pull request, CI builds the patches, applies them to the official
      AARE APK, runs the tests and the security checks; merge to `main` only when green
- [ ] One table of texts per language instead of tr()/phrase(); every patch follows the system language
      (English when missing)
- [ ] Spoken yes/no from Dicio's util_yes_no sentences, for the assistant and voice reply
- [ ] "What's the weather?" from AARE's own Open-Meteo weather and the car's outside temperature (no new
      connection; if AARE's weather is off, say to turn it on)
- [ ] Timers by voice, locally (AlarmClock.ACTION_SET_TIMER)
- [ ] Requirements table in the README: minimum Android version per patch and what the car needs (e.g. car
      microphone Android 13+, voice messages and integrated Shizuku Android 12+, tunnel navigation a car with GPS)

## v1.12 – Phase B

- [ ] Dicio (offline, Vosk) as optional speech recognizer in Settings; default stays the phone's recognizer with
      the car microphone. With Dicio: phone microphone only, same language as the assistant
- [ ] Use Dicio only if enabled and its signing certificate is the real Dicio's
- [ ] Automatic fallback to the phone's recognizer on Dicio errors, with a clear message
- [ ] Later: propose EXTRA_AUDIO_SOURCE support to Dicio upstream (car microphone)

## v1.13 – Phase C

- [ ] Dicio's command matcher (reference sentences, StandardScore, captures) in the assistant; best match above
      a threshold, else the phone's assistant; confirmations unchanged
- [ ] Languages: it, de, en, es, fr, nl, sv, tr (non-native-checked ones marked beta, tested with Car test)
- [ ] AARE-only commands (read messages, reply to…, battery, time left, theme) written for each language
- [ ] Automatic tests: example sentences per language with the expected command
- [ ] Decide Kotlin in the extension vs a Java port; sentences shipped as files or compiled in our build
- [ ] Credits to Dicio in NOTICE and README

## Later

- [ ] Tunnel navigation without the car's GPS: the phone's last GPS fix plus the car's speed, replacing the
      phone's position only inside the tunnel (check feasibility first)
- [ ] Nicknames for contacts in the assistant ("mamma", "amore")
- [ ] "Where did I park?" (position saved when the car disconnects)
- [ ] Telegram / Nekogram voice messages (check where they are stored first)
- [ ] Mixed Italian + German recognition (German street names)
- [ ] "Take me home" passed to the navigation app (check that Vela accepts it)
- [ ] Backup and restore of the patch settings
- [ ] Tested-cars list in the README, from the "Car test" reports
- [ ] pt, zh, ja, ar for the assistant (not in Dicio; zh/ja need their own word splitting)

## Not planned

- Dicio as AARE's assistant (opens on the phone, no car tools, no car microphone)
- Dicio's weather (OpenWeatherMap) and forecasts: new connections. Forecasts could be proposed to AARE upstream.
- Fuel and tyre alerts

## Outside the code

- [x] Discussion on prototux/AARE proposing the features upstream
- [x] Reply to mgitp on prototux/AARE#324
- [ ] Car report for the Skoda Octavia 2022 (MIB3)
