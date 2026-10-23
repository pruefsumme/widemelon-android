# WideMelon for Android
Play DS games without the black bars. WideMelon renders extra 3D at the sides of the top screen so it fills a phone
or TV display, while the 2D HUD stays where the game put it. It is the Android version of the
[WideMelon](https://github.com/pruefsumme/widemelon) desktop build and is based on WatermelonDS, which itself builds
on [melonDS](https://melonds.kuribo64.net/) and the [melonDS Android port](https://github.com/rafaelvcaetano/melonDS-android)
by rafaelvcaetano.

### Widescreen
*  **Settings > WideMelon** has everything in one place. The defaults (Auto width, full screen bottom swap) work for
   most phones, so you shouldn't need to touch anything
*  Real widescreen needs the **OpenGL** renderer. Other renderers stretch the picture instead (or keep 4:3 if you prefer)
*  The top screen fills the display. Tap the new **bottom screen** button (or map *Toggle Bottom Screen* to a
   controller button) to switch to the touch screen and back. Floating overlay and side by side modes are available too
*  On devices with two displays (AYN Thor and similar) the wide top screen goes on the main display and the bottom
   screen on the second one automatically
*  Quick toggles live in the in-game menu under **Widescreen & Screens**
*  Some games only draw what the original 4:3 view could see, so objects can pop in at the edges. Hardcore
   RetroAchievements sessions always run at the native width

The core changes live in [pruefsumme/melonDS-android-lib](https://github.com/pruefsumme/melonDS-android-lib) on the
`widemelon` branch.

[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/pruefsumme/widemelon-android/releases/latest)

<p align="center">
   <img width="450" height="400" alt="WaterMelon1" src="https://github.com/user-attachments/assets/187ea254-877e-4efd-a8dd-93a6023684ad" />
   <img width="450" height="400" alt="WaterMelon1" src="https://github.com/user-attachments/assets/01e82877-a138-44a3-bc71-c24d48cabab6" />
   <img width="450" height="400" alt="WaterMelon1" src="https://github.com/user-attachments/assets/27868058-ce89-470d-afac-b812dea5d1de" />
</p>


# What WatermelonDS adds
Everything from the upstream Android port, plus:

### RetroAchievements
*  Achievements, leaderboards and Hardcore mode, with a shared card style for the in-game popups
*  Your profile and score are shown on load and in the settings screen
*  **Offline play**: unlocks earned without a connection are stored and submitted later. Two providers are available
   the one built into WatermelonDS, or [RAOfflineProxy](https://github.com/SapphireRhodonite) if you prefer to run it
   yourself. Note that RAOfflineProxy does not support Hardcore mode
*  Achievements can be disabled without logging out, and sessions start even for games that have no achievement set

### Renderers
*  Software, OpenGL, Vulkan and compute renderers, with internal resolution scaling
*  Vulkan gained a *fastpath* presentation profile, optimized texture uploads and dynamic scissor clipping
*  **RetroArch shader presets** on every renderer, through [librashader](https://github.com/SnowflakePowered/librashader)
*  Adrenotools driver support on the `gitHub` flavor, for custom Adreno drivers

### External displays
*  Play on a second screen (TV, dock, devices such as the Odin 2), with the touch screen still usable on the handheld
*  Per-ROM external screen and layout settings, screen rotation, backgrounds, video filtering and aspect ratio control
*  Achievements are shown on the external display while you browse your ROM list

### Library and layouts
*  DSiWare titles in the ROM list, box art on the "continue" shelf, and custom ROM names
*  Layout editor with screen transparency, aspect ratio options and separate centering controls
*  Settings backup and restore (requires a restart to apply), with internal and external layouts backed up separately
*  Alternate input bindings and a hold-to-fast-forward button

The core is kept in sync with melonDS 1.0 and later upstream changes.

# Missing Features
*  Local Multiplayer
*  DSi SD card support
*  Customizable button skins

# Performance
Performance is solid on 64 bit devices with thread rendering and JIT enabled, and should run at full speed on flagship
devices. Performance on older devices, specially 32 bit devices, is very poor due to the lack of JIT support.

# Integration with third-party frontends
It's possible to launch WatermelonDS from third party frontends. For that, you will need to have the ROMs you want to
launch already scanned by WatermelonDS. Then, you can configure your third-party frontend with the following
configuration:
*  Package name: `me.magnum.melondualds` (nightly builds use `me.magnum.melondualds.nightly`)
*  Activity name: `me.magnum.melonds.ui.emulator.EmulatorActivity`
*  Parameters (choose one):
    * Intent data (preferred) - a URI of the NDS ROM (ZIP and 7z files are supported). Ensure [read permission is granted](https://developer.android.com/reference/android/content/Intent#FLAG_GRANT_READ_URI_PERMISSION)
    * `uri` (deprecated) - a string with the [SAF](https://developer.android.com/guide/topics/providers/create-document-provider) URI of the NDS ROM (ZIP and 7z files are supported)
    * `PATH` (deprecated) - a string with the absolute path to the NDS ROM (ZIP and 7z files are supported)


### Info regarding save files
When launching ROMs from third-party frontends, if WatermelonDS hasn't scanned that particular ROM previously, it won't
be able to create the save file next to the ROM file if the option "Save next to ROM file" is enabled in the settings or
the save file directory is not set. Instead, WatermelonDS will create a save file in
`Android/data/me.magnum.melondualds/files/saves`

# Releases

Builds are published [here](https://github.com/SapphireRhodonite/melonDS-android/releases). Release candidates are
marked as pre-releases; they can contain more bugs than usual and you may need to clear your app data to get them to
work properly after an update.

# Building
To build the project you will need the Android SDK, NDK, CMake, a JDK 21 toolchain and a Rust toolchain
(`cargo` and `rustup`), which is used to build librashader for each ABI.

## Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/SapphireRhodonite/melonDS-android.git`
2.  Install the Android SDK, NDK (`28.0.13004108`) and CMake
3.  Install Rust with [rustup](https://rustup.rs/). If Android Studio does not inherit your shell `PATH`, set the
    `CARGO` and `RUSTUP` environment variables to the executable paths
4.  Build with:
    1.  Unix: `./gradlew :app:assembleGitHubProdDebug`
    2.  Windows: `gradlew.bat :app:assembleGitHubProdDebug`
5.  The generated APK can be found at `app/build/outputs/apk/gitHubProd/debug`

There are two flavor dimensions, so build tasks combine both: `gitHub` or `playStore` (the former enables adrenotools)
and `prod` or `nightly`. For example, `:app:assembleGitHubNightlyDebug` or `:app:assembleGitHubProdRelease`.

If you want to create a release build, you will need to modify your `local.properties` file to include the following fields:  
*  `MELONDS_KEYSTORE=<path_to_your_keystore>`
*  `MELONDS_KEYSTORE_PASSWORD=<keystore_password>`
*  `MELONDS_KEY_ALIAS=<name_of_your_key_alias>`
*  `MELONDS_KEY_PASSWORD=<key_alias_password>`

# Credits
*  [melonDS](https://github.com/melonDS-emu/melonDS) by Arisotura and contributors — the emulator core
*  [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) by rafaelvcaetano
*  [librashader](https://github.com/SnowflakePowered/librashader) — RetroArch shader preset support
*  [rcheevos](https://github.com/RetroAchievements/rcheevos) — RetroAchievements support
