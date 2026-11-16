# Shoot the Scene – Android app

A native Android app that runs the Shoot the Scene web game full-screen.

## Get a test APK (GitHub Actions)
1. Push this folder to your GitHub repo (GitHub Desktop → Commit → Push).
2. On github.com open your repo → **Actions** → the latest **Build APK** run.
3. When it shows a green check (a few minutes), download the APK under **Artifacts**.
   It arrives as a .zip; unzip it to get `ShootTheScene-buildN.apk`.
4. Copy it to your phone, tap it, and allow "install unknown apps" once.

New builds install over the old one, so saved progress is kept.
To rebuild without changing anything: Actions → Build APK → **Run workflow**.

## Update the game
Replace `web/index.html` with your new version, then commit and push.
Any extra files (images, .js, .css) also go in `web/`.

## Common tweaks
| What | Where |
|---|---|
| App name on home screen | `app/src/main/res/values/strings.xml` |
| Lock to portrait / landscape | `android:screenOrientation` in `app/src/main/AndroidManifest.xml` (`portrait` or `sensorLandscape`) |
| App icon | `app/src/main/res/drawable/ic_launcher_foreground.xml` (placeholder camera) |
| Load the live Netlify site instead of the bundled copy | `REMOTE_URL` in `MainActivity.java` |

## Debugging on a phone
Plug the phone into a computer by USB (USB debugging on), open desktop Chrome at
`chrome://inspect`, and click **inspect** under Shoot the Scene for full DevTools.

## Notes
- The app needs internet for Google Fonts and the Unsplash scene photos.
- `navigator.share` opens Android's normal share sheet; links open in the right app.
- `app/debug.keystore` is a **test-only** signing key. Before the Play Store you'll
  create a private release key and keep it out of the repo.

## Android Studio
Open this folder with **File → Open**. Run ▶ to install on a connected phone, or
**Build → Build APK(s)**.
