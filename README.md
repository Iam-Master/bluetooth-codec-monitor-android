# Codec Monitor Android

A native Android application designed to monitor your Bluetooth Audio Codecs in real-time. This is the Android equivalent of the popular Windows Codec Monitor app.

It uses modern Jetpack Compose and native Kotlin flows to poll your Android Bluetooth Audio subsystem and accurately report which high-res codec (LDAC, aptX, AAC, SBC) your True Wireless Stereo (TWS) earbuds are actively using.

## Features
- **Native Android 12+ UI:** Built beautifully with Jetpack Compose (Material 3). Requires Android 12 (API 31) or newer.
- **Real-Time Codec Monitoring:** See exactly which codec your headphones are using.
- **Estimated Bitrate Dashboard:** Unlike Windows, Android doesn't natively expose the air transmission bitrate. This app intelligently estimates your streaming bitrate based on your active codec, negotiated sample rate, bit depth, and signal quality (RSSI).
- **No Device Specific Code:** Built using generic Android `BluetoothA2dp` and `BluetoothCodecStatus` APIs, making it universally compatible with any Bluetooth audio device.

## Installation / Download

You can download the latest version of the app directly from the Releases page:

1. Go to the **[Releases](../../releases/latest)** tab on this GitHub repository.
2. Under "Assets", tap on the `app-release.apk` file to download it to your Android device.
3. Open the downloaded APK file. (If prompted, allow your browser to "Install from Unknown Sources").
4. Tap **Install** and open the app!

## How to Use

1. **Connect your headphones:** Ensure your TWS earbuds are paired and connected to your Android phone via Bluetooth.
2. **Play Audio:** The Bluetooth codec often drops to a low-power standby state when nothing is playing. Start playing music or a video on your phone to engage the active high-quality codec.
3. **Open Codec Monitor:** Launch the app. It will request permission to find nearby Bluetooth devices. Once granted, your active codec, sample rate, and estimated bitrate will instantly appear on the dashboard.

## Building from Source

To compile this project yourself:
1. Clone this repository: `git clone https://github.com/Iam-Master/codec-monitor-android.git`
2. Open the directory in **Android Studio**.
3. Let Gradle sync.
4. Click **Run**.

## License
[MIT License](LICENSE)
