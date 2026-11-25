VEXOR MIC BOOSTER — Android Studio starter project

Open this folder in Android Studio, allow Gradle sync, connect your Android device, and press Run.
Minimum Android: 8.0 (API 26). Target SDK: 35.

Implemented in this prototype:
- VEXOR dark/cyan Jetpack Compose interface
- Microphone permission and live microphone input-level meter
- MP3 file picker using Android's system document picker
- MP3 local playback, pause/stop, and playback volume slider
- Real-time microphone monitoring with adjustable gain, bass, treble and clarity DSP; Gaming/Deep Bass/Clear presets

Important limitations:
- Mic gain/bass/treble/clarity controls process the live microphone signal for local headphone monitoring using a lightweight real-time DSP chain. This is not a studio-grade equalizer.
- MP3 audio plays locally through the phone output. It is NOT mixed into or injected into WhatsApp/game microphone input. Live mic monitoring is also local output only.
- On a non-rooted Android phone, a regular third-party app cannot generally replace another app's microphone source system-wide. Android input-sharing rules can also silence one app's mic capture when a voice-communication app is using the microphone.
- Speaker playback may be picked up acoustically by the phone mic, but echo cancellation/noise suppression may remove it and quality is not guaranteed.

Next engineering stage:
1. Test live DSP monitoring with wired headphones on the target phone.
2. Test Android audio input sharing and device-specific routing on the exact Xiaomi/Redmi/POCO model.
3. Only claim WhatsApp/game transmission if verified on-device.

References:
https://developer.android.com/media/platform/sharing-audio-input
https://developer.android.com/reference/android/media/MediaPlayer
https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT

Safety: Start with low volume and use wired headphones. Do not monitor through the phone speaker at high gain because acoustic feedback can occur.
