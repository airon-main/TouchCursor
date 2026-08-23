# KeyMouse

A small Android 8+ mouse/touch layer for keypad phones, remotes and keyboards.

**Not a D-pad pretending to be a mouse.** KeyMouse keeps a real synthetic touch pointer down while you hold the mouse button, so drag and scroll happen while you move — not after you release.

## What works

- 8 bindable movement directions (plus simultaneous cardinal keys for diagonals)
- left button = real DOWN → MOVE → UP drag
- right/context button = Android long-press (Accessibility backend limitation)
- touch-scroll mode = one continuous held pointer, not repeated swipe spam
- cursor styles: crosshair, arrow, plus, ring, dot
- per-action key calibration using key code + scan code
- direct hardware-key mode **or** external Intent control for Key Mapper / Tasker / Automate
- no AndroidX, no analytics, no network permission

Minimum Android: **8.0 / API 26**. Intended first target: keypad/garaho devices such as F-03L-class hardware.

## Install / use

1. Build/install the APK.
2. Open KeyMouse and bind your keys.
3. Enable **KeyMouse input service** in Android Accessibility settings.
4. Hold the left-button key and move: the target app receives a continuous touch drag immediately.
5. Toggle **Touch-scroll mode** to keep the virtual finger held while moving the cursor.

If another remapper such as Key Mapper must own the physical keys, turn off **Capture hardware keys directly** and trigger KeyMouse with broadcasts instead.

## External Intent API

For Key Mapper, the easiest aliases need no extras:

- `dev.keymouse.action.CURSOR_TOGGLE`
- `dev.keymouse.action.TOUCH_TOGGLE`
- `dev.keymouse.action.LEFT_DOWN` / `dev.keymouse.action.LEFT_UP`
- `dev.keymouse.action.RIGHT_CLICK`
- `dev.keymouse.action.MOVE_UP`, `MOVE_UP_RIGHT`, `MOVE_RIGHT`, etc. (one movement pulse)

Use receiver component `dev.keymouse/dev.keymouse.input.ExternalTriggerReceiver`. Key Mapper supports custom broadcast Intent actions.

Example:

```sh
adb shell am broadcast -a dev.keymouse.action.CURSOR_TOGGLE -n dev.keymouse/.input.ExternalTriggerReceiver
adb shell am broadcast -a dev.keymouse.action.LEFT_DOWN -n dev.keymouse/.input.ExternalTriggerReceiver
adb shell am broadcast -a dev.keymouse.action.LEFT_UP   -n dev.keymouse/.input.ExternalTriggerReceiver
```

There is also a generic API for automation tools that support string extras: action `dev.keymouse.input.DOWN`, `UP` or `PULSE`, with string extra `key` set to a `MouseAction` name.

## Build

```sh
gradle :app:assembleDebug
```

Requires JDK 17+ and Android SDK 35. The GitHub Actions workflow builds the debug APK automatically on push.

## Important limitation

Accessibility can synthesize touch gestures, not a true hardware `BUTTON_SECONDARY` mouse event. Therefore `RIGHT_BUTTON` is implemented as a context long-press. A future optional Shizuku/root backend can provide genuine mouse `MotionEvent` injection.

## Why this exists

The project is a clean-room implementation inspired by the shortcomings of D-pad cursor utilities: delayed long presses, repeated-swipe scrolling, fixed bindings and exclusive key capture. No DPTV Cursor source code is copied.

License: Apache-2.0.
