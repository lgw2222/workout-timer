# Interval Timer

One timer, two ways to run it:

- **Android app** (floating mini timer when you switch apps): built automatically by GitHub.
  Download the newest `IntervalTimer.apk` from **Releases → Latest**.
- **Web app** (iPhone, computers): GitHub Pages serves `index.html` from this repo.

`index.html` is the only file to edit. Every push to `main` rebuilds the Android app with it.

## Install on Android
1. Open this repo's **Releases** page on your phone, tap `IntervalTimer.apk`.
2. Allow Chrome to install unknown apps when asked, then tap Install.
3. Updates: download the newer APK and install over the old one. Saved workouts stay.

## Files
- `index.html`, `manifest.json`, `sw.js`, icons: the web app
- `android/`: the Android app wrapper (floating window, keep screen on, vibration)
- `.github/workflows/android.yml`: builds the APK
