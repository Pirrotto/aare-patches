# AARE Background Location Patches

A [Morphe](https://morphe.software) patch for [AARE](https://github.com/prototux/AARE), the open-source Android Auto replacement.

## ❓ About

AARE asks Android for the *location* foreground-service type, so the navigation app shown on the car screen (for example Vela Maps) keeps its GPS while the phone is locked. When AARE starts the car connection in the background, for example from the car's Bluetooth connection, Android 14 and later refuse that type unless the app holds the background location permission, which AARE doesn't declare. AARE then runs without it, and the navigation app freezes on the car screen as soon as the phone screen turns off.

The **Background location** patch adds that one permission to AARE's manifest. It changes nothing else.

The patch only accepts the official AARE releases, signed with the AARE release key published in its README.

### How to use

1. Add this repository to Morphe Manager: tap the button below, or add `https://github.com/Pirrotto/aare-patches` as a patch source.

   <a href="https://morphe.software/add-source?github=Pirrotto/aare-patches">
     <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
   </a>

2. Download the official `aare-<version>.apk` from the [AARE releases](https://github.com/prototux/AARE/releases) and patch it with Morphe Manager.
3. Uninstall the official AARE (the patched app has a different signature), then install the patched one and set AARE up again.
4. In **Settings → Apps → AARE → Permissions → Location**, choose **Allow all the time**.

Navigation apps then keep following the car with the phone locked, with no need to open AARE before starting the car.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/Pirrotto/aare-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`main`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**1 patches** across **1 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**AARE**](PATCHES.md#aare-netprototuxaare) | 1 | `1.5.0` | [`net.prototux.aare`](https://play.google.com/store/apps/details?id=net.prototux.aare) |
<!-- PATCHES_END -->

## 🧑‍💻 Building

- Make changes on the `dev` branch and use [semantic commit](https://www.conventionalcommits.org) messages (`feat:`, `fix:`, `chore:`). `release.yml` publishes the releases.
- Local build: `./gradlew buildAndroid`, then find the bundle in `patches/build/libs/patches-*.mpp`.

Based on the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information. This project is not affiliated with Morphe or with AARE.

## 📜 License

AARE Background Location Patches are licensed under the [GNU General Public License v3.0](LICENSE).
