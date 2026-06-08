# HeliBoard (Soniox voice input)

A custom HeliBoard build with **Soniox** integration for voice dictation (speech-to-text).

## What changed

The main addition is **real-time dictation via Soniox Realtime STT**.

- Voice input uses the Soniox WebSocket API (default model: `stt-rt-preview`)
- A **dedicated microphone key** sits to the left of Enter — no need to open the toolbar
- While recording, the key shows state: blue pulse while connecting, green while listening (brightness follows your voice level)
- Optional sound and vibration when the microphone is actually ready for speech

## Setup

**Settings → Voice & privacy**

1. **Voice input provider** — select `Soniox API`
2. **Soniox API key** — key from [console.soniox.com](https://console.soniox.com)
3. **Voice key placement** — `Dedicated key (left of Enter)` for the standalone mic button

## Build

```powershell
.\gradlew assembleDebugNoMinify
```

APK output: `app/build/outputs/apk/debugNoMinify/HeliBoard_3.9-debugNoMinify.apk`

## Download

Pre-built APK: [latest release](https://github.com/Art-emg/HeliBoard/releases/latest)

## License

Based on HeliBoard (GPL-3.0). See [LICENSE](/LICENSE).
