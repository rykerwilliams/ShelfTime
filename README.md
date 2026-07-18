# ShelfTime

![banner](https://github.com/mkaflowski/Audiobookshelf-WearOS/blob/main/raw/banner%20small.jpg?raw=true)

[![Build Debug APK](https://github.com/rykerwilliams/ShelfTime/actions/workflows/build-apk.yml/badge.svg)](https://github.com/rykerwilliams/ShelfTime/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/rykerwilliams/ShelfTime)](https://github.com/rykerwilliams/ShelfTime/releases/latest)

[Audiobookshelf](https://github.com/advplyr/audiobookshelf) for Android WearOS.

---

## About

This is a fork of [mkaflowski/ShelfTime](https://github.com/mkaflowski/ShelfTime), a standalone Wear OS client for Audiobookshelf. The original maintainer hasn't merged a PR or pushed a commit since September 2025, so several long-open community fixes and feature requests were sitting unreviewed. This fork picks those up and continues active development.

**This build is not published on the Play Store.** Install it by sideloading — grab the latest APK from [Releases](https://github.com/rykerwilliams/ShelfTime/releases), then see [SIDELOADING.md](SIDELOADING.md) for step-by-step install instructions (building from source or grabbing an unreleased branch's debug build from [GitHub Actions](https://github.com/rykerwilliams/ShelfTime/actions) both work too).

The original project's README is preserved at [README.original.md](README.original.md) for reference.

---

## Features

- **User Authentication** 🔒: Securely log in to your Audiobookshelf server.
- **Library Browsing** 📚: Browse all audiobooks on the server, sorted by what you last listened to.
- **Chapters** 🔍: View chapter information for each audiobook.
- **Playback** 🎧: Listen directly on your watch, with rewind/fast-forward controls and a short automatic rewind when resuming from a real pause.
- **Sleep Timer** 🌙: Set playback to stop after 15/30/45/60 minutes.
- **Downloads** ⬇️: Download full audiobooks for offline listening, without the Wi-Fi slowdowns of the original app.
- **Smart Delete** 🧹: Automatically remove your oldest downloads once you pass a configurable limit, without ever touching whatever you're currently listening to.
- **Progress Sync** 🔄: Listening progress syncs with the server automatically.
- **Offline Mode** 📴: Keep listening to downloaded books with no connection; progress syncs once you're back online.
- **Search** 🔎: Filter your library by title or author.
- **Sideloadable Config** ⚙️: Skip retyping your server/login on the watch's tiny keyboard — push a JSON file and it pre-fills (or auto-logs in). See [SIDELOADING.md](SIDELOADING.md).

## What's different from the original app

- Fixed a Wear OS 6 / Pixel Watch 4 compatibility crash (foreground service startup, a wrong permission check, a double ExoPlayer release).
- Fixed slow downloads on the same Wi-Fi network as your server (the watch was aggressively downclocking Wi-Fi in the background).
- Added a sleep timer.
- Added Smart Delete, so a large library doesn't fill up your watch's storage.
- Library now sorts by most-recently-progressed instead of most-recently-modified.
- Rewinds a few seconds when you resume from an actual pause, not just when opening a book fresh.
- Fixed playback stopping outright when the watch screen turns off (ExoPlayer held no wake lock at all).
- Fixed the download progress bar freezing instead of updating live.
- Fixed slow playback start (an unused debug log line was running a SQLite query against the download index, once per track, on the main thread, before playback could begin).
- A round of battery/performance fixes: Wi-Fi lock only held while actually downloading, cover art decoded off the main thread and downsampled, redundant network/polling loops collapsed, notification rebuilds throttled.
- Sideloadable config file, so a reinstall doesn't mean retyping your server and login on the watch.
- First unit tests and instrumented (Wear OS emulator) tests in the project's history, both run on every push via GitHub Actions, with code coverage reporting.
- [Releases](https://github.com/rykerwilliams/ShelfTime/releases) with prebuilt APKs, instead of only building from source.

## Roadmap

- [ ] OIDC / SSO login support
- [ ] Pagination for large libraries
- [ ] Getting unfinished audiobooks

## Screens

See [docs/SCREENS.md](docs/SCREENS.md) for a full walkthrough of every screen,
its interactions, and how navigation between screens works, plus
auto-generated screenshots.

## Installing

See [SIDELOADING.md](SIDELOADING.md).

## Credits

Built on top of [mkaflowski/ShelfTime](https://github.com/mkaflowski/ShelfTime) and incorporates fixes from its community forks and open PRs, including work by [PhilippM7](https://github.com/PhilippM7), [Karlmit](https://github.com/Karlmit), and [Andead2](https://github.com/Andead2).
