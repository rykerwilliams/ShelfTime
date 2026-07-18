# Sideloading a custom build

The Play Store build of ShelfTime doesn't include the fixes/features from this
fork (sort order, Wi-Fi download fix, sleep timer, Smart Delete, etc). To get
them onto your watch, you install the APK directly instead of through the
Play Store — this is called sideloading.

## 1. Get the APK

The easiest way: grab the latest prebuilt APK from
[**Releases**](https://github.com/rykerwilliams/ShelfTime/releases) — download
`ShelfTime-vX.Y.apk` from the newest release.

To try an unreleased branch instead, every push to this repo also builds a
debug APK via GitHub Actions:

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

## 4. Pre-configure login and settings without typing on the tiny screen

After installing (or reinstalling — steps 3b and every OS-level app-data wipe
lose your saved login and settings), the app will read a config file if you
push one to its external files directory, and use it to fill in the login
screen (or log you in automatically) and/or preset any of the app's Settings.

1. Copy [`shelftime-config.example.json`](shelftime-config.example.json) from
   the repo root to `shelftime-config.json` and fill in whichever fields you
   want — every field is optional, and any you leave out (or delete from the
   file) just falls back to the normal default (typing it manually, for the
   login fields, or the in-app default otherwise):

   ```json
   {
     "protocol": "http",
     "serverAddress": "192.168.1.50:13378",
     "login": "your-username",
     "password": "your-password",
     "jumpBackwardSeconds": 10,
     "jumpForwardSeconds": 30,
     "offlineMode": false,
     "smartDeleteEnabled": true,
     "smartDeleteMaxDownloads": 5,
     "smartDeleteMaxBytes": 2000000000,
     "bezelMode": "Scrub",
     "tapToPlayEnabled": true
   }
   ```

   | Field | Type | Meaning |
   | --- | --- | --- |
   | `protocol` | `"http"` or `"https"` | Server protocol. Only applied if no server address is already saved. |
   | `serverAddress` | string | Your Audiobookshelf server's address, e.g. `192.168.1.50:13378`. Only applied if no server address is already saved. |
   | `login` | string | Username. Only applied if no login is already saved. |
   | `password` | string | Password. Only applied if no password is already saved. If both `login` and `password` are provided (and neither was already saved), the app logs in automatically without needing a tap. |
   | `jumpBackwardSeconds` | integer | Rewind button's jump amount. Same setting as the Settings screen's "Jump Backward" stepper (5-60, in steps of 5). |
   | `jumpForwardSeconds` | integer | Fast-forward button's jump amount. Same setting as the Settings screen's "Jump Forward" stepper (5-60, in steps of 5). |
   | `offlineMode` | `true`/`false` | Skips network calls the app would otherwise make (see the in-app Offline Mode setting). |
   | `smartDeleteEnabled` | `true`/`false` | Whether old downloads get automatically cleaned up once a limit below is reached. |
   | `smartDeleteMaxDownloads` | integer | Smart Delete's max number of downloaded books kept at once (1-20). |
   | `smartDeleteMaxBytes` | integer | Smart Delete's max total download size, in bytes (e.g. `2000000000` = 2GB). |
   | `bezelMode` | `"Scrub"`, `"Volume"`, or `"Off"` | What the watch's rotating bezel/crown does on the Now Playing screen. |
   | `tapToPlayEnabled` | `true`/`false` | Whether tapping a book in the list jumps straight into playback when nothing's currently playing, or always opens the chapter list instead. |

   The four login fields (`protocol`/`serverAddress`/`login`/`password`) are
   one-time secrets: they only fill in if you haven't already configured that
   value, so pushing the file again later won't silently overwrite a login
   you've since changed in the app. Every other field is an ordinary
   preference default, applied unconditionally whenever present -- so you can
   deliberately re-push the file later with new values to change any of them.

2. Push it to the app's external files directory (same directory the app
   already uses for downloads — no storage permission needed). The
   filename has to be exactly `shelftime-config.json`:

   ```bash
   adb push shelftime-config.json /sdcard/Android/data/kaf.audiobookshelfwearos/files/shelftime-config.json
   ```

3. Launch the app. It reads the file once, applies whatever fields were
   provided, then deletes the file so the plaintext password doesn't linger
   on disk.

Keep your filled-in `shelftime-config.json` around (e.g. alongside the APK)
so you can re-push it after any future reinstall.

## 5. Pulling performance logs

Every debug build writes a rolling log file to the app's external files
directory — the same one used for downloads and the config file above — so
you can use the app normally for a while and pull the log afterward instead
of needing a live `adb logcat` session running the whole time. It's capped
at 5 files x 2MB, oldest rotated out first.

```bash
adb pull /sdcard/Android/data/kaf.audiobookshelfwearos/files/logs/ ./shelftime-logs/
```

Beyond the app's normal diagnostic logging, look for lines tagged `Perf` —
those are battery/memory snapshots taken at the moments that actually drain
a watch battery (playback starting/stopping, a download session's Wi-Fi
lock being acquired/released), so you can see the before/after delta around
a specific session instead of just a raw log stream.

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
