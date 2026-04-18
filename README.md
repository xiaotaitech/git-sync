# GitSync

[![Build APK](https://github.com/xiaotaitech/git-sync/actions/workflows/build.yml/badge.svg)](https://github.com/xiaotaitech/git-sync/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/xiaotaitech/git-sync?include_prereleases&label=download)](https://github.com/xiaotaitech/git-sync/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

An Android app that syncs GitHub repositories to local folders — designed for [Obsidian](https://obsidian.md/) users who want Git-backed vaults without a desktop.

## Features

- Add GitHub repos via Personal Access Token (PAT)
- Two-way sync: auto-commit local changes, rebase, and push
- Conflict detection with a resolution dialog
- Background sync via WorkManager
- Sync log with per-repo history
- Material 3 UI (dark/light theme)

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Git:** JGit 6.9.0 (no system Git required)
- **DI:** Hilt
- **DB:** Room + DataStore
- **Background:** WorkManager

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 26+
- A GitHub [Personal Access Token](https://github.com/settings/tokens) with `repo` scope

### Build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Open the app and go to **Settings** → enter your GitHub PAT
2. Tap **+** to add a repository (owner/repo + local folder)
3. Tap **Sync** on any repo card to pull/push changes
4. Background sync runs automatically via WorkManager

## Architecture

```
app/src/main/java/com/gitsync/
├── data/          # Room entities, DAOs, DataStore prefs
├── di/            # Hilt AppModule
├── git/           # GitSyncManager, ConflictDetector
├── notification/  # SyncNotifier
├── ui/            # Compose screens (repolist, addrepo, settings, synclog, conflict)
├── util/          # SAF helpers, path utils
└── worker/        # SyncWorker (WorkManager + Hilt)
```

## Security Notes

- PAT is stored in DataStore (not WorkManager InputData) to avoid exposure in job history
- Never commit your PAT to source control

## License

MIT
