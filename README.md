# KeyCursor

Hardware-key mouse and touch cursor for Android 8+.

KeyCursor turns hardware keys into a real touch cursor with movement, click-and-hold, drag, a persistent Hold mode and real Accessibility scroll actions. The cursor is hidden until you hold the configured **Mouse key**.

## Features

- 8-direction movement and fully configurable bindings
- real touch DOWN / MOVE / UP for click, hold and drag
- three mouse modes: Normal, Hold and Scroll
- single Mouse-key press toggles Normal / Hold; double press selects Scroll
- Scroll mode uses Android Accessibility scroll actions on the scrollable element under the cursor
- optional edge wrapping
- configurable cursor style, color and size
- separate portrait / landscape direction inversion
- optional landscape control rotation with the display
- optional blending of simultaneously held directions into intermediate angles
- system light / dark theme
- optional broadcast control for Key Mapper, Tasker, Automate or ADB
- no network permission, analytics or ads

Package: `ru.vasys.keycursor`

Minimum Android: 8.0 / API 26

Tested on **Fujitsu F-03L** (Android 8.1).

## Setup

1. Install KeyCursor.
2. Open **Accessibility settings** from the app and enable **KeyCursor input service**.
3. Bind **Mouse key** and any directions your device does not report as the standard D-pad.
4. Hold Mouse key to show or hide the cursor.
5. While visible, single-press Mouse key to toggle **Normal / Hold** or double-press it for **Scroll**.

**Normal** moves the cursor and uses the click binding for click/drag. **Hold** keeps a virtual finger down while the cursor moves. **Scroll** uses the direction bindings as directional scroll actions instead of moving the cursor.

When the cursor is hidden, keys pass through normally. While it is visible, movement/click/cursor-switch keys are consumed so Android focus does not move at the same time. Mouse key itself stays pass-through.

## Direction behavior

Hardware bindings are matched as physical keys first. Android-rotated D-pad key codes are normalized so the default mapping stays stable after rotation.

In settings you can additionally:

- **Rotate controls with screen in landscape** — physical Up rotates into the screen direction after the device is turned;
- **Blend simultaneous directions** — adjacent held directions are combined into an intermediate angle while preserving constant pointer speed;
- invert left/right separately in portrait and landscape;
- invert up/down separately in portrait and landscape.

Direction blending happens before landscape rotation and inversion.

## External control

External broadcasts are disabled until **Allow external control** is enabled in KeyCursor.

Show / hide:

```sh
adb shell am broadcast \
  -a ru.vasys.keycursor.action.MOUSE_TOGGLE \
  -n ru.vasys.keycursor/.input.ExternalTriggerReceiver
```

Other actions include `MODE_TOGGLE`, `CURSOR_NEXT`, `LEFT_DOWN`, `LEFT_UP` and `MOVE_*`. `MODE_TOGGLE` switches Normal / Hold.

## Build

Requires JDK 17 and Android SDK 35.

```sh
./gradlew :app:assembleDebug
```

## Notes

Android allows only one enabled Accessibility service to own hardware key filtering at a time. If another accessibility remapper owns that stream, use KeyCursor's external broadcast backend instead.

Cursor artwork derived from Pictogrammers Material Design Icons is listed in `THIRD_PARTY_NOTICES.md`.

KeyCursor itself is MIT licensed. See `PRIVACY.md` for data handling.
