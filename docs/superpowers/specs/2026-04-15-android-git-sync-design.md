# Android Git Sync App — Design Spec

**Date:** 2026-04-15
**Purpose:** Android app that syncs GitHub repositories to local folders, primarily for Obsidian vault synchronization via GitHub.

---

## Overview

A native Android app that allows users to configure multiple GitHub repositories, each mapped to a local folder, and pull the latest changes either manually or on a schedule.

---

## Technical Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Git:** JGit (self-contained, no system git required)
- **Background tasks:** WorkManager
- **Local storage:** Room (SQLite)
- **Icons:** Material Symbols

---

## Authentication

- **Method:** Personal Access Token (PAT)
- PAT is stored globally in Settings, used for all repositories
- Token is displayed obfuscated (masked) in the UI

---

## Core Features

### Repository Management
- Add multiple GitHub repositories
- Each repo maps to a distinct local folder (selected via system file picker)
- Supports clone on first add, pull on subsequent syncs
- Repos are stored in Room database

### Sync Behavior
- **Manual:** User taps sync button on a repo card
- **Scheduled:** Per-repo configurable interval — Off / 15min / 30min / 1h / 6h
- WorkManager handles background scheduling, survives app restarts

### Conflict Handling
When local modifications are detected before a pull:
1. A notification is sent to the user
2. User taps notification → ConflictDialog opens
3. Dialog lists modified files
4. Two options:
   - **覆盖本地 (Force pull):** Discard local changes, hard reset to remote
   - **保留本地，跳过 (Skip):** Keep local changes, skip this sync cycle

---

## Architecture

```
app/
├── ui/
│   ├── RepoListScreen        # Main screen: list of repo cards
│   ├── AddRepoScreen         # Add / edit repo configuration
│   ├── SyncLogScreen         # Sync history log
│   ├── SettingsScreen        # Global PAT + default interval
│   └── ConflictDialog        # Modal: conflict resolution
├── data/
│   ├── RepoDao               # Room: repo config CRUD
│   └── SyncLogDao            # Room: sync history records
├── git/
│   ├── GitSyncManager        # JGit wrapper: clone / pull / status
│   └── ConflictDetector      # Detect local modifications pre-pull
├── worker/
│   └── SyncWorker            # WorkManager background task
└── notification/
    └── SyncNotifier          # Sync status + conflict notifications
```

### Data Flow

1. User adds repo → stored in Room → `GitSyncManager.clone()` triggered
2. WorkManager wakes on schedule → calls `GitSyncManager.pull()`
3. `ConflictDetector` checks for local changes before pull
4. If conflict → `SyncNotifier` fires notification → user resolves in `ConflictDialog`
5. Pull result written to `SyncLog` → UI card updates reactively via Flow

---

## UI Design

### Navigation
Bottom navigation bar with 3 tabs:
1. **仓库** (Repositories) — main screen
2. **日志** (Logs) — sync history
3. **设置** (Settings) — PAT + global config

### RepoListScreen (Main)
- Card list, one card per repo
- Each card shows:
  - Repo name + local path
  - Last sync time
  - Status badge: `同步中` / `已同步` / `有冲突` / `失败`
  - Manual sync button (spinning animation during sync)
- FAB for adding a new repo

### AddRepoScreen
- GitHub repo URL input
- Local folder picker (Android Storage Access Framework)
- Sync interval selector: Off / 15min / 30min / 1h / 6h

### ConflictDialog
- Lists locally modified files
- Two action buttons: 「覆盖本地」/ 「保留本地，跳过」

### SettingsScreen
- PAT token input (masked display)
- Global default sync interval

---

## Visual Design System

| Property | Value |
|----------|-------|
| Theme | Dark (OLED) primary, system light/dark toggle |
| Background | `#09090B` |
| Primary | `#2563EB` (blue) |
| Success | `#22C55E` (green) — synced |
| Warning | `#F97316` (orange) — conflict |
| Error | `#EF4444` (red) — failed |
| Typography | Inter / system sans-serif fallback |
| Card radius | 16dp |
| Card border | 1dp, white alpha 0.08 |
| Icons | Material Symbols (Compose) |
| Min touch target | 48×48dp |
| Spacing system | 4dp / 8dp base grid |

### Animation
- Sync button: continuous rotation while syncing (150–300ms easing)
- Card state transitions: crossfade, 200ms ease-out
- Status badge color transitions: animated on state change

---

## Permissions Required

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (or Scoped Storage via SAF)
- `INTERNET`
- `FOREGROUND_SERVICE` (WorkManager periodic tasks)
- `POST_NOTIFICATIONS` (Android 13+)

---

## Out of Scope

- Push / commit support
- SSH key authentication
- iOS / cross-platform support
- Conflict merging (only force-overwrite or skip)
- Branch switching
