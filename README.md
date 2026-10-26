<p align="center">
  <img src="app/src/main/res/drawable-nodpi/watermelon_icon_default.png" width="160" alt="WideMelon">
</p>

<h1 align="center">WideMelon for Android</h1>

<p align="center">
  Play DS games without the black bars.
  <br>
  <a href="https://github.com/pruefsumme/widemelon-android/releases/latest">Download</a> ·
  <a href="https://github.com/pruefsumme/widemelon-android/releases/tag/nightly">Nightly</a> ·
  <a href="https://github.com/pruefsumme/widemelon">Desktop version</a>
</p>

<p align="center">
  <a href="https://github.com/pruefsumme/widemelon-android/actions/workflows/nightly.yaml"><img src="https://github.com/pruefsumme/widemelon-android/actions/workflows/nightly.yaml/badge.svg" alt="Nightly"></a>
  <a href="https://github.com/pruefsumme/widemelon-android/releases/latest"><img src="https://img.shields.io/github/v/release/pruefsumme/widemelon-android?include_prereleases&label=release" alt="Release"></a>
</p>

---

WideMelon renders the 3D world wider than the DS ever did, so the top screen fills a modern phone or TV instead of
sitting in a 4:3 box. The 2D HUD stays exactly where the game put it. It's the Android counterpart of the
[WideMelon desktop build](https://github.com/pruefsumme/widemelon), built on top of WatermelonDS and
[melonDS](https://melonds.kuribo64.net/).

## Features

**Widescreen**
* The top screen fills the whole display. **Auto** picks the right width for your screen, or choose 16:9, 21:9 or a
  custom width
* Real widescreen needs the **OpenGL** renderer, which is the default since 1.1.0 (installs from 1.0.0 are switched
  over on update). Other renderers stretch the picture instead, or keep 4:3 if you prefer, and the game tells you so
  when it starts

**Bottom screen on demand**
* A button in the touch overlay switches between the wide top screen and the touch screen
* With a controller, map *Toggle Bottom Screen* to any button in the input settings
* Pick how it shows up: **full screen swap** (default), a **floating overlay** in the corner, or **side by side**

**Dual screen devices**
* On handhelds with two displays, like the AYN Thor, the wide top screen goes on the main display and the bottom
  screen on the second one automatically

**Everything from WatermelonDS**
* RetroAchievements with offline play, Vulkan and compute renderers, RetroArch shader presets, external display
  support, layout editor, DSiWare and more

## Getting started

1. Install the APK from the [releases page](https://github.com/pruefsumme/widemelon-android/releases/latest)
2. Start a game. That's it, OpenGL is the default renderer so widescreen works out of the box. If the picture still
   looks stretched, check **Settings > Video > Renderer** and the game's own settings, both need to be OpenGL. Fine tuning lives in **Settings > WideMelon**, quick toggles in the in-game menu under
   **Widescreen & Screens**

WideMelon installs next to WatermelonDS and melonDS, it doesn't replace them. Nightly builds install next to the stable
version too.

## Good to know

* DS games were only designed for a 4:3 screen. Some only draw what fits in that view, so the sides can stay empty,
  objects can pop in at the edges, or the picture can flicker. That's down to how the game was made. Switch
  widescreen off for those games (in-game menu › *Widescreen & Screens*)
* **Photosensitivity warning:** flickering can trigger seizures in people with photosensitive epilepsy. If a game
  flickers, turn widescreen off
* 2D only scenes (menus, maps) keep black bars on the sides, since there is no 3D to extend
* Hardcore RetroAchievements sessions always run at the native width, so results stay fair
* Dual screen support is written against the AYN Thor display setup but hasn't been tested on real hardware yet.
  Reports are welcome

## Third party frontends

Launch WideMelon from ES-DE, Daijishō and similar with:

* Package: `io.github.pruefsumme.widemelon` (nightly: `io.github.pruefsumme.widemelon.nightly`)
* Activity: `me.magnum.melonds.ui.emulator.EmulatorActivity`
* Intent data: the ROM URI (ZIP and 7z work too) with read permission granted

The ROM has to be scanned by WideMelon first, otherwise saves go to `Android/data/io.github.pruefsumme.widemelon/files/saves`.

## Building

You need the Android SDK with NDK `28.0.13004108` and CMake, JDK 21 and Rust installed through
[rustup](https://rustup.rs/) (librashader is built from source).

```sh
git clone --recurse-submodules https://github.com/pruefsumme/widemelon-android.git
cd widemelon-android
./gradlew :app:assembleGitHubProdDebug
```

The APK ends up in `app/build/outputs/apk/gitHubProd/debug`.

Before pushing, run `./gradlew :app:verify`. It runs the unit tests and the release lint checks, the same as CI. To have
it run automatically on every `git push`, enable the bundled hook once with `git config core.hooksPath .githooks`.

For signed release builds add these to `local.properties`:

```properties
MELONDS_KEYSTORE=/path/to/keystore.jks
MELONDS_KEYSTORE_PASSWORD=...
MELONDS_KEY_ALIAS=...
MELONDS_KEY_PASSWORD=...
```

The widescreen core changes live in [pruefsumme/melonDS-android-lib](https://github.com/pruefsumme/melonDS-android-lib)
on the `widemelon` branch, which is checked out as a submodule.

### CI

* **Build** runs tests and a debug build for every branch and pull request
* **Nightly** publishes a signed pre-release from `main`
* **Release** publishes a signed release when a `v*` tag is pushed

## Credits

* [melonDS](https://github.com/melonDS-emu/melonDS) by Arisotura and contributors
* [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) by rafaelvcaetano
* [WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS) by SapphireRhodonite
* [librashader](https://github.com/SnowflakePowered/librashader) and [rcheevos](https://github.com/RetroAchievements/rcheevos)

Licensed under the GPLv3, like melonDS.
