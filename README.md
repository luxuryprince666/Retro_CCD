# RetroCCD — Android camera MVP

**RetroCCD** is a native Kotlin / CameraX Android project. It starts directly in the camera, works without an account, and contains no `INTERNET` permission. Capture files are processed locally and written to the public Android MediaStore so they remain in the device gallery after the app is uninstalled.

> Status: source project MVP, not a prebuilt APK. This environment has no Android SDK / Gradle installation and cannot fetch build dependencies, so the APK could not be compiled or device-tested here. See the build steps below.

## Implemented in this MVP

- Rear and front camera switching; front camera preview and stills are mirrored.
- CameraX live camera preview.
- Ten distinct still-photo profiles: `2000S`, `CCD Classic`, `Flash 2K`, `Paper 27`, `Pop Instant`, `Flip VGA`, `DV-99`, `Webcam 01`, `Noir 400`, `Dream 620`.
- Local still processing: tonal profile, saturation/contrast, grain, vignette, low-resolution pixelation, scanlines, JPEG crunch, simple highlight bloom, and selected frame treatments.
- Seven-segment orange LED date stamp baked into processed stills.
- Aspect ratios: 1:1, 4:3, 3:4, 16:9 and 9:16 (applied to the exported still; preview viewport itself remains camera-native).
- Flash Off / Auto / On. Auto uses the ambient-light sensor when the device has one.
- 3-second and 10-second shutter timer; composition grid; shutter sound and haptic feedback.
- MP4 capture through CameraX `Recorder`, written to `Movies/RetroCCD`.
- Four-frame Photo Booth contact sheet.
- Twelve-frame Film Roll: stores twelve processed frames locally until developed, then exports the twelve shots and a 3×4 contact sheet.
- Import of one image from the Android system document picker; it is processed with the selected camera profile.
- Russian and English UI labels based on the device language.
- No account, upload endpoint, analytics SDK, crash-reporting SDK or network permission.

## Important limitations — not yet implemented

The full specification is larger than this first source slice. Do not treat these as completed:

1. **Video look is not baked into the MP4 yet.** The selected look, REC marker and tape overlay are shown on the live preview, but CameraX's video output is currently a clean camera recording. GPU/SurfaceProcessor integration is required for synchronized in-recording color, scanlines, tracking errors and RGB shift.
2. **Live preview is a visual approximation.** Overlay tint, grain texture, vignette, date and scanlines are drawn over CameraX PreviewView. The full still-photo pixel pipeline is applied after capture; the live preview and final JPEG are not yet pixel-identical.
3. Aspect-ratio crop is applied to still exports, not the live preview framing. Flash Auto depends on a hardware ambient-light sensor; without one it behaves like Flash Off.
4. Photo Booth is fixed to four frames in this MVP. Film Roll is fixed to twelve frames.
5. Imported video, video editor, video trim/export, extracted video frames, full photo editor (crop UI, sliders, stickers, drawing), manual date/time, alternate date formats/positions/colors, settings persistence and advanced collage templates are not yet implemented.
6. Pro billing, purchase restoration, paid camera packs and locked Pro options are not yet implemented. There are no paywalls or watermarks in this MVP.
7. EXIF date rewriting and custom EXIF metadata are not yet implemented. MediaStore's `DATE_TAKEN` is set at save time.

These limitations are deliberately listed rather than presenting a partially implemented feature as complete.

## Build the debug APK in GitHub Actions (no Android Studio needed locally)

The repository includes `.github/workflows/android-apk.yml`. After pushing the whole project to a GitHub repository, the workflow builds the APK automatically on pushes to `main`/`master`, or manually from **Actions → Build RetroCCD APK → Run workflow**. Download the `RetroCCD-debug-APK` artifact from the completed run. See `CLOUD_BUILD_RU.txt` for the short Russian instructions.

## Build the debug APK on Windows

1. Install the latest stable **Android Studio**.
2. In Android Studio SDK Manager, install **Android SDK Platform 36** and the matching SDK Build-Tools.
3. Ensure Android Studio's Gradle JDK is set to **JDK 17** (Settings → Build, Execution, Deployment → Build Tools → Gradle).
4. Extract this project folder and open the `RetroCCD` folder in Android Studio.
5. Allow the first Gradle sync to download Gradle and AndroidX dependencies. This is a build-time requirement only; the installed app itself has no network permission.
6. In the project terminal, run:

```powershell
.\gradlew.bat assembleDebug
```

The script downloads Gradle 8.13 once into `%USERPROFILE%\.gradle\retroccd\` if it is not already present.

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install on a connected Android device with USB debugging enabled:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Or copy `app-debug.apk` to the phone and open it. Android may ask you to allow installation from that file manager.

## Technical configuration

- Kotlin 2.4.21 / native Android
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- CameraX 1.7.0, AndroidX Activity 1.13.0, Core 1.19.1, ExifInterface 1.4.2
- `compileSdk = 36`, `targetSdk = 35`, `minSdk = 29`
- Java / JVM target 17
- MediaStore storage under `Pictures/RetroCCD` and `Movies/RetroCCD`

## Privacy

The manifest requests only `CAMERA` and `VIBRATE`. It does not request `INTERNET`, broad media access or microphone access. Image import uses Android's document picker, which grants access only to the selected URI. Videos are recorded without audio in this MVP because microphone access is not requested.
