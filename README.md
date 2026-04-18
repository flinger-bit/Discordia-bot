# XDBot Rework

A rework of [xdBot](https://github.com/ZiLko/xdBot) for **Geometry Dash 2.2081** using **Geode SDK 5.6.1**.

Frame-accurate macro recorder and replayer with noclip, TPS bypass, clickbot, speedhack, frame stepper and more.

## Features

| Feature | Description |
|---|---|
| Macro Recorder | Record button inputs with frame accuracy via `m_gameState.m_levelTime` |
| Macro Playback | Replay macros frame-perfectly |
| TPS Bypass | Override physics tick rate with leftOver accumulator |
| Speedhack | Scale game speed + optional FMOD audio pitch |
| Noclip | Pass through objects, shows death count |
| Auto Respawn | Auto-restart on death with configurable delay |
| Clickbot | Sound on button press/release |
| Frame Stepper | Advance one physics frame at a time (N key while paused) |
| Frame Label | HUD showing current frame number |
| Overlay | State + frame count + macro name HUD |

## Building

See [geode-mod/BUILD.md](geode-mod/BUILD.md) for full instructions.

Quick start (Windows):
```bat
set GEODE_SDK=C:\path\to\geode\sdk
cd geode-mod
cmake -B build -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build --config RelWithDebInfo
```

## CI / Download

GitHub Actions automatically builds the `.geode` file on every push to main.  
Download the latest build from the [Actions tab](../../actions) → most recent run → **flinger-bit.xdbot-rework.geode** artifact.

## In-game

Open the **Bot** button in the pause menu (or press **B** while paused).  
Press **N** to advance one frame when Frame Stepper is enabled.

## Targets

- Geometry Dash: `2.2081`
- Geode SDK: `5.6.1`
- Platforms: Windows, Android 32-bit, Android 64-bit
