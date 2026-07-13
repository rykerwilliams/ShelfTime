# Sideloading a custom build

The Play Store build of ShelfTime doesn't include the fixes/features from this
fork (sort order, Wi-Fi download fix, sleep timer, Smart Delete, etc). To get
them onto your watch, you install the APK directly instead of through the
Play Store — this is called sideloading.

## 1. Get the APK

Every push to this repo builds a debug APK automatically via GitHub Actions.

1. Go to the repo's **Actions** tab and open the latest successful
   **Build Debug APK** run for the branch you want.
2. Under **Artifacts**, download `debug-apk` and unzip it to get
   `app-debug.apk`.

(Alternatively, build it yourself locally with `./gradlew assembleDebug` —
the APK lands at `app/build/outputs/apk/debug/app-debug.apk`.)

## 2. Turn on developer options and ADB debugging on the watch

Most Wear OS watches don't have a USB port, so you'll install over Wi-Fi ADB.

1. On the watch: **Settings → System → About → Software** (path varies by
   watch face/version) and tap **Build number** rapidly (5-7 taps) until it
   says developer mode is enabled.
2. Go to **Settings → Developer options** and turn on:
   - **ADB debugging**
   - **Debug over Wi-Fi**
3. The Developer options screen will show an IP address and port
   (e.g. `192.168.1.42:5555`). Keep that screen open — you'll need it.

## 3. Connect and install from your computer

Make sure your computer and watch are on the same Wi-Fi network, then in a
terminal with `adb` installed (comes with Android Studio / platform-tools):

```bash
adb connect 192.168.1.42:5555   # use the IP:port shown on the watch
adb devices                     # confirm the watch shows up as "device"
adb install -r app-debug.apk
```

`-r` reinstalls over an existing install, which you'll want when updating.

## 3b. If you already have the Play Store version installed

Sideloaded builds from this repo are signed with a different key than the
Play Store release, so Android will refuse to install over it with an error
like `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall the Play Store version
first:

```bash
adb uninstall kaf.audiobookshelfwearos
```

Note this wipes local app data (login, offline downloads) — you'll need to
log in again after installing the sideloaded build.

## Troubleshooting

- **`adb connect` fails / times out**: re-check the watch is on the same
  Wi-Fi network as your computer, and that the IP:port shown on the
  Developer options screen hasn't changed (it can change if Wi-Fi
  reconnects).
- **`adb devices` shows the watch as `unauthorized`**: look at the watch
  screen for an "Allow USB/ADB debugging?" prompt and accept it.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: see step 3b above — uninstall
  the existing app first.
- **App installs but crashes/won't log in**: double check the GitHub
  Actions build you downloaded actually succeeded (green check), not just
  ran.
