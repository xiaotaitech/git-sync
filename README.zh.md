# GitSync

[English](README.md)

[![构建 APK](https://github.com/xiaotaitech/git-sync/actions/workflows/build.yml/badge.svg)](https://github.com/xiaotaitech/git-sync/actions/workflows/build.yml)
[![最新版本](https://img.shields.io/github/v/release/xiaotaitech/git-sync?include_prereleases&label=下载)](https://github.com/xiaotaitech/git-sync/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

一款将 GitHub 仓库同步到本地文件夹的 Android 应用，专为希望在手机上使用 Git 备份 [Obsidian](https://obsidian.md/) 笔记库的用户设计。

## 功能特性

- 通过 Personal Access Token（PAT）添加 GitHub 仓库
- 双向同步：自动提交本地修改、rebase、推送
- 冲突检测与可视化解决对话框
- 基于 WorkManager 的后台定时同步
- 每个仓库独立的同步日志记录
- Material3 界面，默认深色主题

## 技术栈

- **语言：** Kotlin
- **UI：** Jetpack Compose + Material3
- **Git：** JGit 6.9.0（无需系统 Git）
- **依赖注入：** Hilt
- **数据存储：** Room + DataStore
- **后台任务：** WorkManager

## 快速开始

### 前置要求

- Android Studio Hedgehog 或更新版本
- Android SDK 26+
- 一个拥有 `repo` 权限的 GitHub [Personal Access Token](https://github.com/settings/tokens)

### 构建

```bash
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

也可直接从 [Releases](https://github.com/xiaotaitech/git-sync/releases/latest) 下载已构建的 APK 安装包。

## 使用方法

1. 打开应用，进入**设置**，填写 GitHub PAT
2. 点击右下角 **+** 添加仓库（填写仓库 URL 和本地文件夹路径）
3. 点击仓库卡片上的**同步**按钮手动触发同步
4. 配置同步间隔后，WorkManager 将在后台自动定时同步

## 项目结构

```
app/src/main/java/com/gitsync/
├── data/          # Room 实体、DAO、DataStore 配置
├── di/            # Hilt AppModule
├── git/           # GitSyncManager、ConflictDetector
├── notification/  # SyncNotifier
├── ui/            # Compose 页面（仓库列表、添加仓库、设置、同步日志、冲突对话框）
├── util/          # SAF 工具、路径工具
└── worker/        # SyncWorker（WorkManager + Hilt）
```

## 安全说明

- PAT 存储在 DataStore 中，不会写入 WorkManager InputData，避免暴露在任务历史记录里
- 请勿将 PAT 提交到版本控制系统

## 开源协议

MIT
