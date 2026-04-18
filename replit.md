# Discordia Terminal

## Project Overview

Discordia Terminal v2.0 is a full VS Code-like IDE Android application built for Samsung SM-X200 (Android 14), compatible with Android 8.0+. It includes a Monaco-powered code editor, Git SCM panel, Extensions marketplace, file manager, HTTP server, terminal, and more.

## Architecture

- **Language:** Kotlin (Android app)
- **UI:** Material Design 3 with View Binding
- **Build System:** Gradle 8.7 with Kotlin DSL (`.gradle.kts`)
- **Android Gradle Plugin:** AGP 8.3.2
- **Min SDK:** 26 (Android 8.0) | Target SDK: 34 (Android 14)
- **Version:** 2.0.0 (versionCode 2)
- **Key Libraries:** NanoHTTPD (HTTP server), Monaco Editor (CDN via WebView)

## Project Structure

```
/app                          - Android application module
  src/main/assets/editor/
    index.html                - Full VS Code-like Monaco IDE (CDN)

  src/main/java/com/discordia/terminal/
    SplashActivity.kt         - Permission request + splash
    MainActivity.kt           - Home screen (10 feature cards)
    TerminalActivity.kt       - Shell UI (connected to ShellEngine)
    ShellEngine.kt            - Built-in command processor (50+ commands)
    FileManagerActivity.kt    - Device file browser
    FileAdapter.kt            - RecyclerView adapter for files
    ServerActivity.kt         - HTTP server control
    LocalServer.kt            - NanoHTTPD implementation
    ServerForegroundService.kt- Background server service
    ProjectBuilderActivity.kt - Project template generator
    SetupActivity.kt          - Setup folder scripts runner
    SetupAdapter.kt           - RecyclerView for setup items
    WorkflowBuilderActivity.kt- GitHub Actions workflow builder
    GitHubManager.kt          - GitHub API client (push/PR/trigger/artifacts)
    WebViewActivity.kt        - Generic WebView wrapper
    SoundManager.kt           - Audio effects
    CodeEditorActivity.kt     - Full IDE via Monaco WebView + JS bridge
    GitActivity.kt            - Git SCM: commit/push/pull/diff/log/branches
    ExtensionsActivity.kt     - Extensions marketplace (28+ extensions)
    SettingsActivity.kt       - Editor/App/GitHub settings

  src/main/res/
    layout/
      activity_main.xml       - 10 card home screen
      activity_code_editor.xml- IDE layout (WebView + toolbar)
      activity_git.xml        - Git SCM panel layout
      activity_extensions.xml - Extensions marketplace layout
      item_extension.xml      - Extension list item
      activity_settings.xml   - Settings scroll layout
      activity_terminal.xml   - Terminal layout
      activity_file_manager.xml - File browser layout
      activity_server.xml     - Server control layout
      activity_project_builder.xml
      activity_setup.xml
      activity_workflow_builder.xml
      activity_splash.xml
      activity_webview.xml
      item_file.xml           - File list item
      item_setup.xml          - Setup item

    values/
      colors.xml              - Full color palette (dark IDE theme)
      strings.xml             - All app strings
      themes.xml              - Material themes + QuickButton style

  AndroidManifest.xml         - All activities + permissions + FileProvider

/server.js                    - Express server (port 5000) serving landing page
/public/                      - Landing page HTML/CSS/JS

/.github/workflows/Build.yml  - GitHub Actions: builds debug + release APKs
```

## GitHub Actions

- Repo: `flinger-bit/Discordia-bot`
- Every push to `main` triggers a build (debug APK + release APK signed)
- Token stored as Replit secret `GITHUB_PERSONAL_ACCESS_TOKEN`
- APK artifacts downloadable from the Actions run page

## Features (v2.0)

1. **VS Code IDE** — Monaco editor via WebView, 10+ themes, syntax highlighting, IntelliSense, minimap, multi-cursor, tabs, file explorer, command palette, built-in terminal panel, Git panel, search, extensions panel
2. **Terminal** — 50+ built-in commands (head/tail/wc/sort/uniq/free/ps/kill/diff/stat/file/alias/source/which + network + archive + git + python/node passthrough)
3. **Git SCM** — commit/push/pull/diff/log/branches/clone/init via GitHubManager API
4. **File Manager** — full file browser + editor + share
5. **Extensions Marketplace** — 28+ extensions catalog with install/uninstall
6. **Settings** — editor (theme/font/tabsize/minimap/wordwrap/autosave), app (sound/dark mode/dir), GitHub (token/owner/repo)
7. **Local HTTP Server** — NanoHTTPD, foreground service
8. **Mod Builder** — project template generator
9. **Workflows** — GitHub Actions trigger + monitor + artifact download
10. **Setup Folder** — script runner

## Replit Server

- `server.js` runs an Express server on port 5000 serving a project info landing page
- Deployment type: autoscale
