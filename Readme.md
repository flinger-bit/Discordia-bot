# Discordia Terminal

**A powerful Android terminal app for Samsung SM-X200 (Android 14)**

[![Build APK](https://github.com/flinger-bit/Discordia-bot/actions/workflows/Build.yml/badge.svg)](https://github.com/flinger-bit/Discordia-bot/actions/workflows/Build.yml)

---

## Features

- **Full Terminal** — Execute shell commands directly on your device (ls, cd, cat, mkdir, grep, and more)
- **All Files Access** — Requests MANAGE_EXTERNAL_STORAGE permission for full filesystem control
- **Local HTTP Server** — Built-in NanoHTTPD server to browse files over WiFi from any browser
- **File Manager** — Navigate, browse, and open files on your device
- **Project Builder** — Template generator for:
  - Minecraft Java mods (Forge 1.20.1)
  - Minecraft Bedrock addons
  - Android app templates
  - Web projects (HTML/CSS/JS)
  - Shell scripts

## Download APK

After each push, GitHub Actions builds the APK automatically.

👉 **[Download from Actions → Build Discordia Terminal APK → Artifacts](https://github.com/flinger-bit/Discordia-bot/actions)**

## Requirements

- Android 8.0 (API 26) or higher
- Recommended: Android 14 (API 34)
- Samsung SM-X200 or any Android tablet/phone

## Building Locally

```bash
# Requires Java 17 + Android SDK
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Kotlin** — Primary language
- **Material Design 3** — UI components
- **NanoHTTPD** — Embedded HTTP server
- **AndroidX** — Lifecycle, RecyclerView, CardView
- **Gradle 8.7 + AGP 8.3.2** — Build system

## Architecture

```
SplashActivity    → Requests MANAGE_EXTERNAL_STORAGE, then goes to Main
MainActivity      → Home screen with 4 feature cards
TerminalActivity  → Shell interface powered by ShellEngine
FileManagerActivity → File browser backed by FileAdapter
ServerActivity    → LocalServer (NanoHTTPD) control panel
ProjectBuilderActivity → Template generator for mods/apps
```

---

*Created with Discordia Terminal — SM-X200 Edition*
