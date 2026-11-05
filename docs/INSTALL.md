# Installing Orbit TV

## Just give me the APK

Open [**Releases**](https://github.com/HashirAKB/orbit-tv-iptv/releases/latest) on your phone,
download `OrbitTV.apk`, tap it, and allow your browser to install apps when Android asks.

Requires Android 7.0 (API 24) or newer. Roughly 20 MB of channel data is downloaded on first launch
and cached for 24 hours.

---

## Building it yourself

### On a computer

```bash
git clone https://github.com/HashirAKB/orbit-tv-iptv.git
cd orbit-tv-iptv
gradle assembleRelease
```

The APK lands at `app/build/outputs/apk/release/app-release.apk`. JDK 17 is the only prerequisite —
Gradle fetches the Android SDK components it needs, and you may be asked to accept SDK licences once
(`sdkmanager --licenses`).

Or open the folder in Android Studio (Ladybug or newer) and press Run.

### From a phone, with no computer at all

GitHub Actions does the compiling; your phone only needs a browser.

1. **Fork this repo** — the Fork button works fine in mobile Chrome.
2. In your fork, open the **Actions** tab and enable workflows if prompted.
3. Open the **Release** workflow and tap **Run workflow**.
4. Wait about four minutes, then open your fork's **Releases** page. `OrbitTV.apk` is attached.
5. Download it and tap to install.

Everything you change in the fork rebuilds the same way, so this is also the fastest loop for
trying a patch without a development machine.

### Signing

Release builds are signed with the standard Android debug key. That is deliberate: CI can then
produce an immediately installable APK with no secrets configured. It is fine for sideloading and
unsuitable for Play Store distribution.

If you fork this to publish properly, add a real keystore and replace this in
[`app/build.gradle.kts`](../app/build.gradle.kts):

```kotlin
release {
    signingConfig = signingConfigs.getByName("debug")   // ← replace
}
```

## Troubleshooting

**"App not installed" / "Install blocked."**
Android needs per-app permission to install from your browser. Settings → Apps → your browser →
Install unknown apps → Allow. If an older Orbit TV is already installed and was signed with a
different key, uninstall it first.

**Stuck on the loading screen.**
It is fetching ~20 MB from `iptv-org.github.io`. On a slow connection this takes a while. If it
fails outright it falls back to any cached copy; with no cache you get a Retry button.

**A channel shows "Stream unavailable."**
Orbit already tried every mirror it has for that channel. The link is dead upstream — report it at
[iptv-org/iptv](https://github.com/iptv-org/iptv/issues), not here.

**Everything looks empty after enabling the offline filter.**
The eye icon on Home toggles it. Tap it to show channels whose streams are known-dead anyway.

**A stream plays in VLC but not in Orbit.**
That one *is* ours — please
[open an issue](https://github.com/HashirAKB/orbit-tv-iptv/issues/new?template=dead_channel.yml).
