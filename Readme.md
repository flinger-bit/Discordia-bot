# Discordia Terminal

**A powerful Android terminal app for Samsung SM-X200 (Android 14)**

[![Build APK](https://github.com/flinger-bit/Discordia-bot/actions/workflows/Build.yml/badge.svg)](https://github.com/flinger-bit/Discordia-bot/actions/workflows/Build.yml)

---

## Features

- **Terminal** — Execute shell commands directly on your device (ls, cd, cat, mkdir, grep, find, cp, mv, and more). Any unlisted command runs through the Android shell automatically.
- **All Files Access** — Requests `MANAGE_EXTERNAL_STORAGE` on first launch for full filesystem control
- **Local HTTP Server** — Built-in NanoHTTPD server to browse and serve files over WiFi from any browser
- **File Manager** — Navigate and open files anywhere on device storage
- **Setup Folder** — Your personal script hub at `/sdcard/DiscordiaSetup/` (see below)
- **Project Builder** — Template generator for Minecraft mods, Android apps, web projects, shell scripts

---

## Setup Folder — `/sdcard/DiscordiaSetup/`

The Setup Folder is a **configurable automation hub** created automatically on first launch.

Drop any script or config file here, organize it in subfolders, and run it from inside the app with one tap.

### Supported Script Languages
| Extension | Language |
|-----------|----------|
| `.sh` | Shell / Bash |
| `.py` | Python 3 |
| `.js` | JavaScript / Node.js |
| `.php` | PHP |
| `.rb` | Ruby |
| `.pl` | Perl |
| `.lua` | Lua |
| any | Executable (chmod +x) |

### Pre-built Example Scripts
```
DiscordiaSetup/
  README.txt                    — Getting started guide
  website/
    start-server.sh             — Launch a local HTTP server serving your files
  security/
    set-password.sh             — Store SHA-256 hashed passwords per service
  tools/
    install-python.sh           — Install Python via Termux
    install-nodejs.sh           — Install Node.js via Termux
    download-file.sh            — Download any file via curl/wget
  minecraft/
    new-java-mod.sh             — Scaffold a Minecraft Java mod (Forge 1.20.1)
    new-bedrock-addon.sh        — Scaffold a Minecraft Bedrock addon
  system/
    device-info.sh              — Show device specs, storage, IP, RAM
    cleanup.sh                  — Clean temp files, show disk usage
  custom/                       — Your own scripts go here
```

### How to use
1. Open **Setup Folder** from the home screen
2. Navigate into any subfolder
3. Tap a script → choose **Run / Execute**
4. Output appears live in the bottom panel
5. Use **+ Script** or **+ Folder** to add your own
6. Drop files into `/sdcard/DiscordiaSetup/` using the File Manager or a PC

---

## Download APK

After each push to `main`, GitHub Actions automatically builds fresh APKs.

👉 **[Download from Actions → Artifacts](https://github.com/flinger-bit/Discordia-bot/actions)**

| APK | Size | Notes |
|-----|------|-------|
| `discordia-terminal-debug.apk` | ~4.78 MB | Install directly (sideload) |
| `discordia-terminal-release.apk` | ~3.80 MB | Unsigned release build |

---

## Requirements

- Android 8.0 (API 26) or higher
- Recommended: Android 14 (API 34) — Samsung SM-X200

## Building Locally

```bash
# Requires Java 17 + Android SDK + Gradle 8.7
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
SplashActivity          → Permission request (MANAGE_EXTERNAL_STORAGE) + splash
MainActivity            → Home screen (5 feature cards)
TerminalActivity        → Shell UI with history, autocomplete
  ShellEngine.kt        → Built-in command processor (20+ commands)
FileManagerActivity     → Device file browser
  FileAdapter.kt        → RecyclerView adapter
ServerActivity          → Local HTTP server control
  LocalServer.kt        → NanoHTTPD implementation
  ServerForegroundService.kt → Background server service
ProjectBuilderActivity  → Project template generator
SetupActivity           → Setup folder script manager
  SetupAdapter.kt       → Script/folder list adapter
```

## Tech Stack
- **Kotlin** — Primary language
- **Material Design 3** — UI
- **NanoHTTPD** — Local HTTP server
- **AndroidX** — Lifecycle, RecyclerView, CardView, ConstraintLayout
- **Gradle 8.7 + AGP 8.3.2** — Build system

---

*Discordia Terminal — SM-X200 Edition • Built with GitHub Actions*
