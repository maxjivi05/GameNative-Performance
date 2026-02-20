# GameNative-Performance

GameNative-Performance is a high-performance fork of GameNative, optimized specifically for Adreno-powered devices (Snapdragon 8 Gen 1/2/3/Elite). It introduces aggressive clock management, internal file system access, and robust save game portability.

## New Features (Performance Variant)

### 🚀 Hardware Performance Optimization
- **Force Maximum Clocks (Non-Root):** Integrated loop detection via Adreno Tools that re-applies maximum GPU clock requests every 5 seconds to prevent system downclocking.
- **Root Maximum Performance:** For rooted users, an aggressive **500ms loop** that monitors hardware nodes and instantly rewrites GPU registers if a frequency drop is detected.
- **Aggressive Power Profiles:** Forces Adreno power levels to 0 (Maximum) and disables GPU "napping" to maintain peak FPS in demanding titles.
- **Fuzzy GPU Node Detection:** Automatically identifies and targets device-specific clock nodes (e.g., hitting 1200MHz on Adreno 840).

### 📂 Advanced File Management
- **Embedded File Explorer:** A full-screen internal file manager with Multi-select, Copy, Cut, Paste, and Delete capabilities. Access `/data/data/app.gamenative` without needing root.
- **GNP Documents Provider:** Exposes the app's internal data directory to external file managers (Solid Explorer, ZArchiver, etc.), making it easy to sideload patches or manually manage data.
- **Enhanced Save Portability:** 
    - **Robust Export:** Packages saves into a standardized `drive_c/` structure with proactive user remapping.
    - **Fuzzy Import:** Intelligent "fuzzy" matching for game titles that ignores spaces/special characters, ensuring saves land in the correct folder even if the ZIP name differs.
    - **Cross-User Compatibility:** Automatically remaps legacy `steamuser` paths to `xuser` for seamless migration from other Winlator/GameHub forks.

### 🛠️ Stability & UX Fixes
- **Async Root Checks:** Root verification no longer freezes the UI; it runs on a background thread with a 5-second safety timeout.
- **Cleaned UI:** Refactored Graphics Settings to remove duplicate VKD3D and DX Wrapper entries, providing a cleaner, more intuitive interface.
- **Error Resilience:** Added extensive try-catch wrapping around native performance calls to ensure the app remains stable on all Android versions.

## How to Use
1. Download the latest release from the Releases.
2. Install the APK on your Android device.
3. Configure your container in **Settings -> Graphics** to enable performance toggles and select Driver.
4. Access internal files via **Settings -> Debug -> Embedded File Access**.

## Support
To report issues or receive support, join our community!

[![](https://img.shields.io/badge/Join%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/fHX2jFUWdP)

## Building
1. Open in Android Studio.
2. Requires **Android SDK 35** and **OpenJDK 17**.
3. Use `./gradlew assembleDebug` to build the performance variant.

## License
[GPL 3.0](https://github.com/maxjivi05/GameNative-Performance/blob/master/LICENSE)

## Credits
1. **Winlator Teams** yes all of them from the Original **Bruno** Builds to CMOD Builds by **CoffinColors** and the Ludashi Builds from **StevenMXZ**.
2. **GameNative Development Team** which is rather large but most of the source here is directly from their hard work and most of the credit goes out to them for their dedication and ever growing team of developers. ( Yes, I steal your open source code and modify it ) thank you.
3. **Box64**, **Fex-Emu**, **DXVK**, **DXVK Gplasync**, **VKD3D**, **VKD3D-Proton**, **Mesa**, Basically everyone who has any piece of software that we use and if I forgot to mention a component let me know and I'll give you credit where credit is due.

## Thank You
I'd like to thank everyone who recommends new features and helps bug test new features, builds and ensure stability on Discord.

**Disclaimer: This software is intended for playing games that you legally own. Do not use this software for piracy. The maintainer assumes no responsibility for misuse.**
