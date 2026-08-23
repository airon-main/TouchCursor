# KeyMouse

A lightweight keyboard / D-pad mouse and touch layer for Android 8+.

- Package: `ru.vasys.keymouse`
- Current version: **0.4.0**
- Minimum Android: **8.0 / API 26**
- Target SDK: **35**
- No AndroidX, network permission, analytics or ads

## What it does

- 8-direction cursor movement with configurable hardware bindings;
- real touch **DOWN / MOVE / UP** for click-hold and drag;
- continuous Scroll mode using Android continued gesture strokes instead of repeated swipes;
- long **Mouse key** press shows/hides the cursor;
- short Mouse-key press while visible switches **Normal / Scroll**;
- optional screen-edge wrapping;
- configurable cursor set, default cursor, color and size;
- automatic light / dark settings UI;
- optional broadcast Intent control for Key Mapper, Tasker, Automate or ADB.

The cursor starts hidden. When hidden, bound D-pad keys pass through normally. While the cursor is visible, movement/click/cursor-switch keys are consumed so Android focus does not move at the same time. The Mouse key itself remains pass-through.

## Setup

1. Install the APK.
2. Open **KeyMouse** and tap **Open Accessibility settings**.
3. Enable **KeyMouse input service**.
4. Bind a **Mouse key** and any non-default directions you need.
5. Hold the Mouse key to show the cursor.

Default bindings are D-pad Up / Right / Down / Left and D-pad Center. Diagonals, Mouse key and Cursor switch start unbound.

## Cursor and touch behavior

The visible cursor and injected touch use the same logical screen coordinate. The overlay exists only while mouse mode is visible.

A held click or Scroll mode uses `StrokeDescription(..., willContinue=true)` and `continueStroke()`, so movement remains one continued synthetic finger rather than a stream of independent swipes. If Android cancels a synthetic gesture, KeyMouse yields and restarts lazily on the next physical movement instead of entering a retry loop.

## Hardware-key compatibility

KeyMouse requests Android's accessibility key-filter capability so it can observe hardware keys before applications.

Android allows only one enabled Accessibility service to own key-event filtering at a time. If another accessibility remapper owns that stream, KeyMouse may not receive direct hardware bindings. Normal applications still receive keys that KeyMouse returns unconsumed.

For remapper-based setups, KeyMouse also provides an optional external Intent backend.

## External control

External control is **disabled by default**. Enable **Allow external control (Key Mapper / Tasker)** in KeyMouse first. When disabled, the exported receiver component itself is disabled.

Show / hide the mouse:

```sh
adb shell am broadcast \
  -a ru.vasys.keymouse.action.MOUSE_TOGGLE \
  -n ru.vasys.keymouse/.input.ExternalTriggerReceiver
```

Other actions include:

```text
ru.vasys.keymouse.action.MODE_TOGGLE
ru.vasys.keymouse.action.MOUSE_KEY_SHORT
ru.vasys.keymouse.action.MOUSE_KEY_LONG
ru.vasys.keymouse.action.MOUSE_KEY_DOWN
ru.vasys.keymouse.action.MOUSE_KEY_UP
ru.vasys.keymouse.action.CURSOR_NEXT
ru.vasys.keymouse.action.LEFT_DOWN
ru.vasys.keymouse.action.LEFT_UP
ru.vasys.keymouse.action.MOVE_UP
... MOVE_UP_RIGHT / MOVE_RIGHT / MOVE_DOWN_RIGHT / etc.
```

Continuous external movement can use `ru.vasys.keymouse.input.DOWN` / `UP` with the string extra `key=MOVE_LEFT`, `MOVE_UP_RIGHT`, etc.

## Runtime / battery behavior

KeyMouse is event-driven while idle:

- no wakelocks;
- no background worker threads;
- no polling timers;
- no UI-tree inspection or accessibility-event subscription;
- no network access;
- no hidden overlay window.

The movement ticker runs only while a movement key is physically held. Display bounds are cached and refreshed on configuration changes instead of being queried every movement frame.

## Build

```sh
./build.sh
```

Or:

```sh
gradle :app:assembleDebug
```

Requires JDK 17+ and Android SDK 35. GitHub Actions builds the debug APK and runs Android lint on every push / pull request.

Cursor assets derived from Pictogrammers Material Design Icons are documented in `THIRD_PARTY_NOTICES.md`.

License: Apache-2.0. See `PRIVACY.md` for data handling.
