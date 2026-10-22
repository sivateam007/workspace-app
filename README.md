# Workspace — AI Code Editor for Android

<p align="center"><b>Kotlin · Jetpack Compose · Room · GitHub API · AI chat</b></p>

**Workspace** is an AI-powered code editor for your phone — open files, write code, ask the AI assistant for a fix, and browse GitHub, all in one Android app.

## ✨ Features

- 📝 **Code editor** — open project files and edit code on the go
- 🤖 **AI assistant** — describe a bug or a feature and get code back inside the app
- 🐙 **GitHub browser** — browse your repositories and files through the GitHub API
- 🗂️ **Projects & recents** — project list and recent files persisted with Room
- 📁 **File manager** — navigate and manage local files
- 🎨 Material theming with light & dark modes

## 🛠️ Tech Stack

- Kotlin + Jetpack Compose
- Room database (`AppDatabase`, `ProjectDao`, `RecentFileDao`)
- AI chat API service + GitHub REST API integration
- MVVM architecture — `ViewModel` + Compose screens

## 📦 Build & Install

1. Open the project in [Android Studio](https://developer.android.com/studio)
2. Build: `./gradlew assembleDebug`
3. Install: `adb install app/build/outputs/apk/debug/app-debug.apk`

## 🤖 Built with AI vibe coding

Designed, coded and tested end to end with **AI vibe coding** — AI prompt coding gives faster results than manual processing.
