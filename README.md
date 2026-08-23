# KeyMouse

Keyboard / D-pad mouse for Android 8+.

KeyMouse turns hardware keys into a real touch cursor: movement, click-and-hold, drag and continuous scrolling. The cursor is hidden until you hold the configured **Mouse key**.

## Features

- 8-direction movement and fully configurable bindings
- real touch DOWN / MOVE / UP for hold and drag
- continuous Scroll mode with Android continued gesture strokes
- short Mouse-key press switches Normal / Scroll while the cursor is visible
- optional edge wrapping
- configurable cursor style, color and size
- separate portrait / landscape direction inversion
- optional landscape control rotation with the display
- system light / dark theme
- optional broadcast control for Key Mapper, Tasker, Automate or ADB
- no network permission, analytics or ads

Package: `ru.vasys.keymouse`

Minimum Android: 8.0 / API 26

## Setup

1. Install KeyMouse.
2. Open **Accessibility settings** from the app and enable **KeyMouse input service**.
3. Bind **Mouse key** and any directions your device does not report as the standard D-pad.
4. Hold Mouse key to show or hide the cursor.

When the cursor is hidden, keys pass through normally. While it is visible, movement/click/cursor-switch keys are consumed so Android focus does not move at the same time. Mouse key itself stays pass-through.

## Direction behavior

Hardware bindings are matched as physical keys first. Android-rotated D-pad key codes are normalized so the default mapping stays stable after rotation.

In settings you can additionally:

- **Rotate controls with screen in landscape** — physical Up rotates into the screen direction after the device is turned;
- invert left/right separately in portrait and landscape;
- invert up/down separately in portrait and landscape.

Inversion is applied after landscape rotation.

## External control

External broadcasts are disabled until **Allow external control** is enabled in KeyMouse.

Show / hide:

```sh
adb shell am broadcast \
  -a ru.vasys.keymouse.action.MOUSE_TOGGLE \
  -n ru.vasys.keymouse/.input.ExternalTriggerReceiver
```

Other actions include `MODE_TOGGLE`, `CURSOR_NEXT`, `LEFT_DOWN`, `LEFT_UP` and `MOVE_*`.

## Build

Requires JDK 17 and Android SDK 36.

```sh
./gradlew :app:assembleDebug
```

### Signed release APK

Create the signing key once and keep it safe. The same key is required for future updates:

```sh
keytool -genkeypair -v \
  -keystore keymouse-release.jks \
  -alias keymouse \
  -keyalg RSA -keysize 4096 -validity 10000
```

Copy `keystore.properties.example` to `keystore.properties` and fill in the real path/passwords:

```properties
storeFile=/absolute/path/to/keymouse-release.jks
storePassword=...
keyAlias=keymouse
keyPassword=...
```

Then build. If `keystore.properties` is absent, Gradle still produces an unsigned release for CI checks; with the file present, the APK is signed automatically.

```sh
./gradlew clean :app:assembleRelease
```

The signed APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.jks` and `*.keystore` are ignored by Git. Do not commit the private signing key.

## Notes

Android allows only one enabled Accessibility service to own hardware key filtering at a time. If another accessibility remapper owns that stream, use KeyMouse's external broadcast backend instead.

Cursor artwork derived from Pictogrammers Material Design Icons is listed in `THIRD_PARTY_NOTICES.md`.

KeyMouse itself is MIT licensed. See `PRIVACY.md` for data handling.
