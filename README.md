# Codec Monitor Android

A native Android application to monitor your Bluetooth audio codecs in real time. This is the Android counterpart to the Windows Codec Monitor app.

It uses Jetpack Compose and native Kotlin coroutines/flows to poll the Android Bluetooth audio subsystem and report which codec (LDAC, aptX, AAC, SBC) your connected True Wireless Stereo (TWS) earbuds or headphones are actively using, along with sample rate, bit depth, and an estimated transmission bitrate.

> **Status: Alpha.** Core monitoring is stable; some features (e.g. headset battery level, automatic product photos) are best-effort and won't work identically on every device — see [Known limitations](#known-limitations) below.

## Features

- **Real-time codec monitoring** — active codec, sample rate, bit depth, and an estimated bitrate for the currently connected device, using the standard `BluetoothA2dp`/`BluetoothCodecStatus` APIs (no scanning, no device-specific drivers).
- **Devices tab** — lists your bonded Bluetooth devices with cached product photos, connection status, and which one is actively streaming audio.
- **History** — every codec/bitrate sample is persisted locally (Room database) with a selectable time range (10m/30m/1h/1d/1w) and a per-device filter. Old rows are pruned automatically based on the retention period set in Settings.
- **Connection stability tracking** — flags a device as Stable / Occasional drops / Unstable based on recent disconnects and codec downgrades.
- **Smart notifications** — optional alerts for connect/disconnect, device switch, and codec upgrade/downgrade/change events, debounced so a flaky connection doesn't spam you.
- **Background monitoring** — an optional foreground service keeps tracking codec/history/alerts while the app isn't in the foreground, with a persistent low-priority status notification (standard Android behavior: it can't be swiped away while the service is running — stop it from its own "Stop monitoring" action or by disabling the setting).
- **Export** — history can be exported as CSV, Markdown, or PDF via Android's document picker.
- **Settings** — poll interval, history retention, notifications on/off, background monitoring on/off, and light/dark/system theme.

## Permissions

The app requests the minimum permission set for what it actually does:

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` | Read bonded devices, connection state, and codec info. **No location permission is requested** — the app never scans for *new* Bluetooth devices, so it doesn't need `BLUETOOTH_SCAN` or the location permission Android ties to it on older versions. |
| `POST_NOTIFICATIONS` | Optional connect/disconnect/codec-change alerts and the background-monitoring status notification. |
| `INTERNET` | Best-effort device product-photo lookup only (see [Privacy](#privacy-network-use) below). The core codec-monitoring feature works fully offline. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required by Android to keep monitoring running while the app is backgrounded, when that setting is enabled. |

## Privacy / network use

The only outbound network traffic this app makes is for the **optional** Devices-tab product photo: it sends the bonded device's name (e.g. "realme Buds Air7") to DuckDuckGo's image search over HTTPS, then downloads a photo from the brand's official site or a major retailer (Amazon/Flipkart/Walmart/Best Buy, with a small last-resort fallback list) before showing it. No other data — Bluetooth MAC addresses, audio content, history, or anything else — ever leaves the device. This entire flow only runs for devices recognized as headphones/earbuds.

## Known limitations

- **Headset battery level** is read via two best-effort, non-privileged Android APIs (`BluetoothDevice.getMetadata` and the classic HFP battery broadcast). There is no public API guaranteed to work for every manufacturer's earbuds — when neither path reports a value, the battery line is simply omitted rather than showing a placeholder.
- **Device product photos** are matched by web search, not an official product-image API, so an exact color/finish match isn't always guaranteed. The fetch logic prioritizes the manufacturer's own site, then major e-commerce listings, and rejects results that look like a different model/variant — but it's inherently best-effort.

## Installation / Download

1. Go to the **[Releases](../../releases/latest)** page on this repository.
2. Under "Assets", download `app-release.apk`.
   - Note: this asset is currently built via `gradle assembleDebug` in CI (debug-signed), not a Play-Store-grade release signature — that's fine for sideloading but means Android will show it as a debug build.
3. Open the downloaded APK. If prompted, allow your browser/file manager to "Install unknown apps".
4. Install and open the app, then grant the Bluetooth permission when asked.

## How to use

1. Pair/connect your headphones via Android's Bluetooth settings as usual.
2. Open Codec Monitor and grant the Bluetooth (and, optionally, notification) permission when prompted.
3. Play audio — codecs often idle at a low-power state until audio actually starts streaming.
4. The Dashboard shows the active codec, sample rate, bit depth, and estimated bitrate live. Check History for past sessions, Devices for all bonded devices, and Settings to configure poll interval, retention, notifications, and background monitoring.

## Building from source

Requires Android Studio (or a standalone Android SDK + JDK 17) and minSdk 31 (Android 12) for testing on a device/emulator.

1. Clone this repository:
   ```
   git clone https://github.com/Iam-Master/bluetooth-codec-monitor-android.git
   ```
2. Open the directory in Android Studio and let Gradle sync, **or** build from the command line:
   ```
   ./gradlew assembleDebug
   ```
3. Run/install via Android Studio, or `./gradlew installDebug` with a device connected over `adb`.

CI (`.github/workflows/build.yml`) builds a debug APK on every push to `main` and uploads it to the `v1.0.0` release as `app-release.apk`.

## License

[MIT License](LICENSE)
