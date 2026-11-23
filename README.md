# AARE Patches

[Morphe](https://morphe.software) patches for [AARE](https://github.com/prototux/AARE), the open-source Android Auto replacement. They add a few things for driving: WhatsApp voice messages and voice replies in the car, and navigation that keeps going in tunnels. Each one can be turned on or off in AARE's settings.

**For AARE 1.6.0.** The patches only accept the official AARE release, signed with the key published in AARE's README.

## 🩹 The patches

| Patch | What it does | Needs |
|---|---|---|
| **Tunnel navigation** | In tunnels the position keeps moving, estimated from the car's speed, compass and gyroscope, until the GPS is back. Off by default. | AARE's *Use the car's GPS* on, a car that sends its GPS |
| **Voice messages** | When AARE reads a message aloud, WhatsApp voice messages play in the car instead of "Voice message (0:05)". Off by default. | Shizuku, allowed for AARE (as for car audio) |
| **Voice reply** | After a message is read aloud: a **Reply** card for 15 s and **Reply** in the notification centre. Dictate, hear it read back, say "yes" or tap **Send**. | Voice messages patch; with AARE's *Car microphone for phone apps* on it listens through the car |
| **Settings** | A **Settings** row at the end of AARE's settings, with one switch per patch. | Included with every patch |

### Tunnel navigation

With *Use the car's GPS*, AARE gives the phone the car's position. In a tunnel the car has no fix, so the navigation app (Vela, Maps, Waze…) stops. The car still sends its speed, compass and gyroscope: from the last good fix the patch estimates where the car is, once a second. The gyroscope's direction is learned from the GPS while driving, so it works in any car, and a compass that freezes in the tunnel is ignored. Other apps get this position too, so it reports an honest accuracy (the last fix's plus 6 % of the distance driven without GPS) and stops after 5 km or 5 minutes of driving; standing in a queue doesn't reset it.

### Voice messages

WhatsApp keeps voice notes in a folder other apps can't read. The patch reads them through Shizuku with a small helper that answers one question only: *"the voice message received at this time, lasting this long?"*. The helper checks WhatsApp's voice notes itself and hands AARE at most that one recording, read-only: AARE never sees a list, a file name or any other voice note. WhatsApp keeps sent and received voice notes together, so a recording counts only if it was saved right after the message arrived and lasts what the notification says: a voice note you sent, or another chat's, is never played. When unsure it says "voice message". No storage permission.

### Voice reply

Works with WhatsApp and any messaging app whose notifications can be answered. Nothing is sent without your confirmation: what you dictated is read back first, and a "no" always wins. It uses the phone's speech recognition; with AARE's *Car microphone for phone apps* on, that hears the car's microphone.

## 📲 How to use

1. In Morphe, add this repository as a patch source: the button below, or `https://github.com/Pirrotto/aare-patches`.

   <a href="https://morphe.software/add-source?github=Pirrotto/aare-patches">
     <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
   </a>

2. Download the official `aare-1.6.0.apk` from the [AARE releases](https://github.com/prototux/AARE/releases) and patch it in Morphe.
3. Uninstall the official AARE (the patched app has a different signature), install the patched one and set AARE up.
4. Turn the patches on in **AARE → Settings → Settings** (the last row).
5. For Voice messages: keep [Shizuku](https://github.com/RikkaApps/Shizuku) running and allowed for AARE.

Patching again later (a new release of the patches) installs over the patched AARE and keeps its settings.

## ✅ In AARE itself

Some patches were proposed to AARE and are now part of it, so they are no longer here:

- **Background location** (navigation with the phone locked): AARE 1.6.0, [#351](https://github.com/prototux/AARE/pull/351).
- **Car microphone** for dictation: AARE 1.6.0's *Car microphone for phone apps*, [#256](https://github.com/prototux/AARE/pull/256).
- **Reply by voice**: proposed for AARE 1.6.1, [#353](https://github.com/prototux/AARE/pull/353).

Tunnel navigation and Voice messages are tested here first, then proposed to AARE.

The patches for **AARE 1.5.0** (with the AARE assistant and Integrated Shizuku) are in [v1.10.6](https://github.com/Pirrotto/aare-patches/releases/tag/v1.10.6), the last release for 1.5.0.

## 🆕 What's new / Novità

**English**

- **2.0 – For AARE 1.6.0.** Background location and Car microphone are now in AARE itself. The AARE assistant and Integrated Shizuku are left out. Tunnel navigation: honest accuracy, 5 km / 5 minute limit, queues, tests. Voice messages: the helper picks the one recording, AARE sees no other file. In a tunnel the position no longer freezes when the car repeats its last fix. Settings → "Copy diagnostics" copies a technical log (no position, names or texts; kept in AARE's private storage after AARE closes, newest 1000 lines) to send when something goes wrong. Reply after a WhatsApp voice message works again. Closing the reply card on the car screen ends the reply and stops the read-back; a long dictated reply is no longer cut off (15 s to start talking, 60 s at most).

**Italiano**

- **2.0 – Per AARE 1.6.0.** Posizione in background e Microfono dell'auto ora sono in AARE. Assistente AARE e Shizuku integrato non ci sono più. Navigazione in galleria: precisione onesta, limite di 5 km / 5 minuti, code, test. Messaggi vocali: il supporto sceglie il solo vocale giusto, AARE non vede altri file. In galleria la posizione non si blocca più quando l'auto ripete l'ultima posizione. Impostazioni → "Copia diagnostica" copia un registro tecnico (senza posizione, nomi o testi; resta nella memoria privata di AARE anche dopo la chiusura, ultime 1000 righe) da inviare quando qualcosa non va. Rispondere dopo un vocale WhatsApp funziona di nuovo. Chiudendo la scheda della risposta sullo schermo dell'auto la risposta finisce e la rilettura si ferma; una risposta lunga non viene più tagliata (15 s per iniziare a parlare, al massimo 60 s).

Every release lists its changes in [the releases](https://github.com/Pirrotto/aare-patches/releases) and in [CHANGELOG.md](CHANGELOG.md).

## 🧾 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.10.6](https://github.com/Pirrotto/aare-patches/releases/tag/v1.10.6)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`main`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**8 patches** across **1 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**AARE**](PATCHES.md#aare-netprototuxaare) | 8 | `1.5.0` | [`net.prototux.aare`](https://play.google.com/store/apps/details?id=net.prototux.aare) |
<!-- PATCHES_END -->

## 🧑‍💻 Development

- Day-to-day work goes to `dev`: every push runs the unit tests, builds the patches and applies them to the official AARE APK, and keeps the patches file as a private test build (an artifact of the run, not a release).
- Public releases come from `main`, at most twice a week (Thursday evening, Saturday evening or Sunday morning), after testing in the car. Commits follow [semantic commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `chore:`).
- Local build: `./gradlew buildAndroid`, then `patches/build/libs/patches-*.mpp`.

Based on the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). Not affiliated with Morphe or with AARE.

## 📜 License

[GNU General Public License v3.0](LICENSE).
