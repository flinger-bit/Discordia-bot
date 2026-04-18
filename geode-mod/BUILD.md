# XDBot Rework — Build Guide

**GD version:** 2.2081  
**Geode version:** 5.6.1  
**Language:** C++20

---

## Requirements

- [Geode SDK](https://github.com/geode-sdk/geode) installed and `GEODE_SDK` env var set
- CMake 3.21+
- MSVC (Windows) / Clang (macOS) / Android NDK r27 (Android)
- Geode CLI (`geode` command available)

---

## Windows

```bat
:: Set SDK path (once)
set GEODE_SDK=C:\geode\sdk

cmake -B build -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build --config RelWithDebInfo
```

The `.geode` file will be in `build/RelWithDebInfo/`.

---

## macOS (Intel)

```bash
export GEODE_SDK=/path/to/geode/sdk

cmake -B build -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -DCMAKE_OSX_ARCHITECTURES=x86_64
cmake --build build
```

---

## Android (arm64)

```bash
export GEODE_SDK=/path/to/geode/sdk
export ANDROID_NDK_HOME=/path/to/ndk/r27

cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-23 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build-android
```

---

## Install

Copy the `.geode` file to your Geode mods folder:

- **Windows:** `%LOCALAPPDATA%\GeometryDash\geode\mods\`
- **Android:** `/data/data/com.robtopx.geometryjump/geode/mods/`
- **macOS:** `~/Library/Application Support/GeometryDash/geode/mods/`

Or use the Geode CLI:

```bash
geode package install build/RelWithDebInfo/flinger-bit.xdbot-rework.geode
```

---

## Features

| Feature | Description |
|---|---|
| **Macro Recorder** | Record button inputs frame-by-frame |
| **Macro Playback** | Replay recorded macros with frame accuracy |
| **Noclip** | Pass through objects without dying |
| **Auto Respawn** | Auto-restart on death with configurable delay |
| **Speedhack** | Scale game speed (0.1× – 10×) |
| **Bot Overlay** | HUD showing state, frame count, macro name |
| **Hitbox Viewer** | Show player hitboxes in practice mode |
| **Frame-Step Mode** | Advance one physics frame at a time (via N key) |
| **Save/Load Macros** | .xbot files stored in Geode save directory |

## In-Game Controls

| Key / Button | Action |
|---|---|
| **Pause → Bot button** | Open bot control panel |
| **B** (while paused) | Open bot control panel (keyboard shortcut) |
| **N** (while playing) | Advance one frame (frame-step mode) |

## Macro File Format

Macros are saved as `.xbot` (JSON) files in:
`<geode-save-dir>/mods/flinger-bit.xdbot-rework/macros/`

```json
{
  "version": 1,
  "author": "flinger-bit",
  "frames": 1234,
  "inputs": [
    { "f": 42,  "p1": true, "h": true,  "b": 1 },
    { "f": 157, "p1": true, "h": false, "b": 1 }
  ]
}
```

Fields: `f` = frame, `p1` = player 1, `h` = hold (true=press, false=release), `b` = button (1=jump, 2=left, 3=right)
