# **XposedFakeLocation**

[![GitHub License](https://img.shields.io/github/license/noobexon1/XposedFakeLocation?style=for-the-badge&color=red&logo=googledocs&logoColor=red)](https://github.com/noobexon1/XposedFakeLocation/blob/master/LICENSE)
[![GitHub Release Date](https://img.shields.io/github/release-date/noobexon1/XposedFakeLocation?style=for-the-badge&label=updated%20in&logo=clockify&logoColor=violet&color=violet)](https://github.com/noobexon1/XposedFakeLocation/releases/latest)
[![GitHub Release](https://img.shields.io/github/v/release/noobexon1/XposedFakeLocation?style=for-the-badge&color=teal&logo=accenture&logoColor=teal)](https://github.com/noobexon1/XposedFakeLocation/releases/latest)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/noobexon1/XposedFakeLocation/total?style=for-the-badge&logo=rolldown&logoColor=blue&label=downloads%20(repo)&color=blue)](https://github.com/noobexon1/XposedFakeLocation/releases)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.noobexon.xposedfakelocation/total?style=for-the-badge&logo=rolldown&label=downloads%20(LSPosed)&logoColor=pink&color=pink)](https://github.com/Xposed-Modules-Repo/com.noobexon.xposedfakelocation/releases)
[![GitHub Repo stars](https://img.shields.io/github/stars/noobexon1/XposedFakeLocation?style=for-the-badge&logo=apachespark&color=yellow)](https://github.com/noobexon1/XposedFakeLocation/stargazers)
![Platform](https://img.shields.io/badge/platform-Android-green.svg?style=for-the-badge&logo=android)

**XposedFakeLocation** is an Android app and Xposed module that spoofs your location for the apps you choose — and, optionally, at the system level — without using "mock location" from the developer options. Pick a spot on the map, walk around with a joystick, or play back a route along real roads.


<div align="center">
    <img src="images/xposedfakelocation.webp" alt="App Logo" width="256" />
</div>


> [!IMPORTANT]
> **This module targets the modern libxposed API (Xposed API 101+).** You must use a recent **LSPosed** build that supports the new API — older managers will not load the module. Get the latest LSPosed from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**.


---

## **Table of Contents**

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Building from Source](#building-from-source)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)
- [Acknowledgements](#acknowledgements)

---

## **Features**

### Spoofing
- **Per-app spoofing**: Choose the target apps inside the app. Your selection drives the LSPosed module scope automatically, so you never manage scope by hand.
- **Optional system-level hooks**: Extend spoofing into `system_server` (`system`) and the phone process (`com.android.phone`) with a single toggle.
- **Wi-Fi identity hooks**: Optionally report a custom Wi-Fi SSID, BSSID and signal strength (RSSI) to target apps and hide real Wi-Fi scan results while spoofing.
- **Fine-tuned location data**: Horizontal/vertical accuracy, altitude, mean sea level (and its accuracy), speed (and its accuracy), GPS noise, and randomization within a radius.
- **Reactive updates**: Force-stop a target app once, the **first** time it's added. After that, changes in the manager (location, settings, start/stop) reach the running app immediately.
- **Root relaunch**: Force-stop and reopen a target app from the Target Apps screen (requires root).

### Map
- **Two map engines**: **MapLibre** (default) shows a sharp vector map with light and dark styles that follow the app theme; **osmdroid** shows the classic OpenStreetMap tiles. You can also use your own MapLibre style URL.
- **Offline maps** (MapLibre): Download the area you're looking at and use the map without a connection. Downloads resume after losing the connection, are named after the place automatically, and can be renamed or deleted in Settings.
- **Address search**: Find places by name and drop the marker or a route point there.

### Movement
- **Joystick**: A floating joystick over other apps lets you walk, run, bike or drive the fake location around while a target app is open.
- **Custom routes**: Build a route on the map and play it back with a music-player style overlay or notification controls. Set speed, wait time and speed variation per section, loop or reverse the route, and save routes to favorites.
- **Road following**: Route sections can follow real roads (car, bike or foot) instead of straight lines. Road paths are saved with the route, so playback works offline.

### App
- **Favorites**: Save locations and routes for quick access.
- **Settings backup**: Export and import all settings, favorites and routes as a file.
- **Headless / external control**: Drive the module from another app or `adb shell` via broadcast intents (off by default).
- **Themes and languages**: Light, dark or system theme; English, German and Chinese.

---

## **Prerequisites**

- **Rooted Android device** with a 64-bit or 32-bit **ARM** processor.
- **Android 10 (API 29)** or newer.
- **Modern LSPosed (new API)**: This module is built against the **libxposed API (Xposed API 101+)**, so it requires a recent [LSPosed](https://github.com/LSPosed/LSPosed) build that supports the new API. Download the latest from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**. Legacy `Xposed`/`EdXposed` and older LSPosed managers are **not** supported.

---

## **Installation**

1. **Install the APK**

   - Download `XposedFakeLocation.apk` from the latest [release](https://github.com/noobexon1/XposedFakeLocation/releases/latest), or install it from the LSPosed manager's module repository.
   - To build it yourself, see [Building from Source](#building-from-source).

2. **Activate the Xposed module**

   - Open a recent **LSPosed Manager** that supports the new API (see [Prerequisites](#prerequisites)).
   - Enable the `XposedFakeLocation` module and reboot once.
   - **Select target apps from inside `XposedFakeLocation`** (the `Target Apps` screen). Your selection updates the module's LSPosed scope automatically.
   - **(Optional) System-level hooks:** to spoof `system_server` (`system`) and the phone process (`com.android.phone`) as well, enable **`Enable system-level hooks`** in `Settings`. This adds those packages to the scope; **reboot** for the change to take effect (and again after turning it off).

> [!NOTE]
> All you need to do in `LSPosed` is **enable the module** — the entire scope is managed from within `XposedFakeLocation`. Don't edit the module's scope manually in `LSPosed`.

---

## **Usage**

1. **Select target apps**

   - Open `Target Apps` from the navigation menu and select the apps that should receive the fake location. Other apps keep their real location.
   - On a rooted device, tap the relaunch button next to an app to force-stop and reopen it so spoofing applies right away.

2. **Pick a location**

   - Tap the map, or use the search button to find a place by name.

3. **Start spoofing**

   - Press `Play` to start and `Stop` to end spoofing. Only the apps in `Target Apps` are affected.
   - **First time only:** when an app is newly added, force-stop and reopen it once (use the relaunch button, or do it manually). After that, every change applies to the running app immediately.

4. **Move around (optional)**

   - **Joystick:** open it from the map to steer the location over other apps. Speeds and presets are in `Settings` → `Joystick`. It needs the "Display over other apps" permission.
   - **Routes:** switch the map to `Route` mode, tap to add points, tap a line between points to set its speed, and press `Play`. Turn on **Follow roads** to move along streets. A route and the joystick can't run at the same time.

5. **Offline maps (optional)**

   - With the MapLibre map, move to the area you need and download it from the map. Manage downloaded areas in `Settings` → `Map` → `Offline areas`.

6. **Configure settings (optional)**

   - Fine-tune location data, Wi-Fi identity, the map engine and style, the search and routing servers, theme and language in `Settings`. Export or import a backup from there as well.

7. **Headless mode (optional, off by default)**

   - Drive the module from another app or `adb shell` via broadcast intents — start/stop and update coordinates without opening the UI. See [`docs/EXTERNAL_CONTROL.md`](docs/EXTERNAL_CONTROL.md) for details.

---

## **Building from Source**

1. **Clone the repository**

   ```shell
   git clone https://github.com/noobexon1/XposedFakeLocation.git
   ```

2. **Build**

   - Open the project in `Android Studio`, let Gradle sync, and run the app on your rooted device.
   - Or build from the command line:

     ```shell
     ./gradlew assembleRelease
     ```

     The APK is written to `app/build/outputs/apk/release/app-release.apk`. Use `assembleDebug` for a faster, unshrunk debug build (`app/build/outputs/apk/debug/app-debug.apk`).

3. **Install**

   ```shell
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   Then activate the module as described in [Installation](#installation).

---

## **Contributing**
Contributions are welcome! Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) for the project structure, coding guidelines, and the pull request process.

---

## **License**

Distributed under the `MIT License`. See [`LICENSE`](LICENSE) for more information.

---

## **Disclaimer**

This application is intended for **development and testing purposes only**. Misuse of location spoofing can violate terms of service of other applications and services. Use at your own risk. There is no responsibility whatsoever for any damage to the device.

---

## **Acknowledgements**

- [GpsSetter](https://github.com/Android1500/GpsSetter) - Highly inspired by this amazing project!
- [libxposed API](https://github.com/libxposed/api) - The modern Xposed API this module is built on.
- [LSPosed](https://github.com/LSPosed/LSPosed) ([Telegram](https://t.me/LSPosed)) - The go-to Xposed framework manager app.
- [MapLibre Native](https://github.com/maplibre/maplibre-native) - Vector map rendering and offline maps.
- [OpenFreeMap](https://openfreemap.org/) - Free map styles and tiles for the MapLibre map.
- [OSMDroid](https://github.com/osmdroid/osmdroid) - Classic OpenStreetMap map view.
- [OpenStreetMap](https://www.openstreetmap.org/copyright) - Map data © OpenStreetMap contributors.
- [Photon](https://photon.komoot.io/) - Address search.
- [OSRM](https://project-osrm.org/) and [routing.openstreetmap.de](https://routing.openstreetmap.de/) - Road routing.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit for Android.
- [Material Design 3](https://m3.material.io/) - Latest design system from Google.
- [Line Awesome Icons](https://icons8.com/line-awesome) - Beautiful icon set used in the app.
- [FuckLocation](https://github.com/Mikotwa/FuckLocation) - Reference for additional Android location hook handling.

## **Star History**

<a href="https://www.star-history.com/?repos=noobexon1%2FXposedFakeLocation&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=noobexon1/XposedFakeLocation&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=noobexon1/XposedFakeLocation&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=noobexon1/XposedFakeLocation&type=date&legend=top-left" />
 </picture>
</a>
