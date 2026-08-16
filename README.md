# **XposedFakeLocation**

[![GitHub License](https://img.shields.io/github/license/noobexon1/XposedFakeLocation?style=for-the-badge&color=red&logo=googledocs&logoColor=red)](https://github.com/noobexon1/XposedFakeLocation/blob/master/LICENSE)
[![GitHub Release Date](https://img.shields.io/github/release-date/noobexon1/XposedFakeLocation?style=for-the-badge&label=updated%20in&logo=clockify&logoColor=violet&color=violet)](https://github.com/noobexon1/XposedFakeLocation/releases/latest)
[![GitHub Release](https://img.shields.io/github/v/release/noobexon1/XposedFakeLocation?style=for-the-badge&color=teal&logo=accenture&logoColor=teal)](https://github.com/noobexon1/XposedFakeLocation/releases/latest)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/noobexon1/XposedFakeLocation/total?style=for-the-badge&logo=rolldown&logoColor=blue&label=downloads%20(repo)&color=blue)](https://github.com/noobexon1/XposedFakeLocation/releases)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.noobexon.xposedfakelocation/total?style=for-the-badge&logo=rolldown&label=downloads%20(LSPosed)&logoColor=pink&color=pink)](https://github.com/Xposed-Modules-Repo/com.noobexon.xposedfakelocation/releases)
[![GitHub Repo stars](https://img.shields.io/github/stars/noobexon1/XposedFakeLocation?style=for-the-badge&logo=apachespark&color=yellow)](https://github.com/noobexon1/XposedFakeLocation/stargazers)
![Platform](https://img.shields.io/badge/platform-Android-green.svg?style=for-the-badge&logo=android)

**XposedFakeLocation** is an Android application and Xposed module that allows you to spoof your device's location for specific apps — and, optionally, at the system level — without using "mock location" from the developer options. Customize your location with precision, including sensor data, and add randomization within a specified radius for enhanced privacy.


<div align="center">
    <img src="images/xposedfakelocation.webp" alt="App Logo" width="256" />
</div>


> [!IMPORTANT]
> **This module now targets the modern libxposed API (Xposed API 101+).** You must use a recent **LSPosed** build that supports the new API — older managers will not load the module. Get the latest LSPosed from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**.


---

## **Table of Contents**

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)
- [Acknowledgements](#acknowledgements)

---

## **Features**

- **Per-App Location Spoofing**: Pick the apps that should receive a fake location directly inside the app — your selection drives the LSPosed module scope automatically, so you never have to manage scope by hand.
- **Optional System-Level Hooks**: Extend spoofing into `system_server` (`system`) and the phone process (`com.android.phone`) for deeper coverage, via a single toggle in Settings.
- **Custom Coordinates**: Set precise GPS latitude and longitude coordinates by tapping the integrated map.
- **Fine-Tuned Spoofing Settings**: Customize sensor values such as horizontal/vertical accuracy, altitude, mean sea level (and its accuracy), speed (and its accuracy), and GPS noise.
- **Randomization**: Set a radius for location randomization to mimic real-world movement patterns.
- **Reactive Updates**: You only need to force-stop and restart a target app the **first** time it's added to the scope. After that, changes you make in the manager app (location, settings, start/stop) reflect in the running target app immediately — no restart required.
- **Root Relaunch**: Force-stop and relaunch a target app straight from the Target Apps screen so spoofing takes effect immediately (requires root).
- **Headless / External Control**: Drive the module from another app or `adb shell` via broadcast intents (off by default).
- **Intuitive UI Navigation**: Easy access to the map, favorite locations, target apps, and settings.

---

## **Prerequisites**

- **Rooted Android Device**: The app requires root access to function properly.
- **Minimum Android Version**: 11 (API 30)
- **Modern LSPosed (new API)**: This module is built against the **libxposed API (Xposed API 101+)**, so it requires a recent [LSPosed](https://github.com/LSPosed/LSPosed) build that supports the new API. Download the latest from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**. Legacy `Xposed`/`EdXposed` and older LSPosed managers are **not** supported.

---

## **Installation**

You can always install the latest stable version of `XposedFakeLocation` from the [releases](https://github.com/noobexon1/XposedFakeLocation/releases) page. 

If you want to build by yourself:

1. **Clone or Download the Repository**

   ```shell
   git clone https://github.com/noobexon1/XposedFakeLocation.git
   ```

2. **Build the Application**

   - Open the project in `Android Studio`.
   - Build the APK using `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   - Alternatively, use `Gradle`:

     ```shell
     ./gradlew assembleDebug
     ```

3. **Install the APK**

   - Install the APK via `adb`:
   
     ```shell
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

4. **Activate the Xposed Module**

   - Open a recent **LSPosed Manager** that supports the new API (see [Prerequisites](#prerequisites)).
   - Enable the `XposedFakeLocation` module and reboot once.
   - **Select target apps from inside `XposedFakeLocation`** (the `Target Apps` screen). Your selection updates the module's LSPosed scope automatically — there's no need to manage scope manually in LSPosed.
   - **(Optional) System-level hooks:** to spoof `system_server` (`system`) and phone process (`com.android.phone`) as well, open `Settings` inside `XposedFakeLocation` and enable **`Enable system-level hooks`**. This adds those packages to the scope; **reboot** your device for the change to take effect (and reboot again after turning it off).

> [!NOTE]
> From now on, all you need to do in `LSPosed` is **enable the module** — the entire scope is managed from within the `XposedFakeLocation` app itself. Adding/removing target apps and toggling system-level hooks updates the LSPosed scope automatically, so you should not edit the module's scope manually in `LSPosed`.

---

## **Usage**

1. **Launch the App**

   - Open `XposedFakeLocation` from your apps menu.

2. **Navigate the Interface**

   - Use the navigation menu to access different sections:
     - **Map**: Primary interface for location selection
     - **Favorites**: Saved locations for quick access
     - **Target Apps**: Apps that should receive spoofed locations.
     - **Settings**: Configure application behavior
     - **About**: View application information

3. **Select Target Apps**

   - Open `Target Apps` from the navigation menu.
   - Search for and select the apps that should receive spoofed locations. Selecting/deselecting an app updates the module's LSPosed scope automatically.
   - Apps not selected here will keep receiving their normal location data.
   - On a rooted device you can tap the relaunch button next to a selected app to force-stop and reopen it so spoofing applies right away.

4. **Select a Location**

   - Use the integrated map to select your desired location by tapping on the map.

5. **Configure Settings**

   - Optionally, access the `Settings` screen to fine-tune your spoofing settings.

6. **Start Spoofing**

   - Toggle the `Play/Stop` button to begin location spoofing.
   - `XposedFakeLocation` will override location data only for apps selected in `Target Apps`.
   - **First time only:** when an app is newly added to the scope, force-stop and reopen it once (use the relaunch button, or do it manually) so the module is loaded into it. After that the module is reactive — any change you make in the manager (location, settings, start/stop) takes effect in the running target app immediately, with no further restarts.

7. **Stop Spoofing**

   - Toggle the `Play/Stop` button to cease location spoofing.

8. **Headless Mode (Optional. Off by default)**
   - Drive the module from another app or `adb shell` via broadcast intents — start/stop and update coordinates without opening the UI. See [`docs/EXTERNAL_CONTROL.md`](docs/EXTERNAL_CONTROL.md) for more details.

---

## **Development**

### **Building from Source**

1. **Clone the Repository**

   ```shell
   git clone https://github.com/noobexon1/XposedFakeLocation.git
   ```

2. **Open in Android Studio**

   - Navigate to the project directory.
   - Open the project with `Android Studio`.

3. **Sync Gradle**

   - Allow Gradle to download all dependencies.

4. **Build and Run**

   - Connect your rooted device.
   - Run the app from `Android Studio`.

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
- [OSMDroid](https://github.com/osmdroid/osmdroid) - Open-source offline map interface.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit for Android.
- [Material Design 3](https://m3.material.io/) - Latest design system from Google.
- [Line Awesome Icons](https://icons8.com/line-awesome) - Beautiful icon set used in the app.
- [FuckLocation](https://github.com/Mikotwa/FuckLocation) - Reference for additional Android location hook handling.

## **Star History**

<a href="https://star-history.dera.page/#noobexon1/XposedFakeLocation&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://star-history.dera.page/svg?repos=noobexon1/XposedFakeLocation&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://star-history.dera.page/svg?repos=noobexon1/XposedFakeLocation&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://star-history.dera.page/svg?repos=noobexon1/XposedFakeLocation&type=date&legend=top-left" />
 </picture>
</a>
