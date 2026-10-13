# KeyCursor

Hardware-key mouse and touch cursor for Android 8+.

KeyCursor turns hardware keys into a real touch cursor with movement, click-and-hold, drag, a persistent Hold mode and real Accessibility scroll actions. The cursor is hidden until you hold the configured **Mouse key**.

## Features

- 8-direction movement and fully configurable bindings
- real touch DOWN / MOVE / UP for click, hold and drag
- three mouse modes: Normal, Hold and Scroll
- single Mouse-key press toggles Normal / Hold; double press toggles Scroll on/off
- Scroll mode uses Android Accessibility scroll actions on the scrollable element under the cursor
- optional edge wrapping
- configurable cursor style, color and size
- separate portrait / landscape direction inversion
- optional landscape control rotation with the display
- optional blending of simultaneously held directions into intermediate angles
- system light / dark theme
- optional Mouse-key capture while the cursor is visible
- touchpad mode: use the whole screen or a movable pad as a trackpad:
  - tap
  - tap-then-hold to drag/scroll
  - configurable two- and three-finger swipes
- Quick Settings tile to show / hide the touchpad
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
5. While visible, single-press Mouse key to toggle **Normal / Hold** or double-press it to enter/leave **Scroll**.

**Normal** moves the cursor and uses the click binding for click/drag. **Hold** keeps a virtual finger down while the cursor moves. **Scroll** uses the direction bindings as directional scroll actions instead of moving the cursor.

When the cursor is hidden, keys pass through normally. While it is visible, movement/click/cursor-switch keys are consumed so Android focus does not move at the same time. **Capture Mouse key while cursor is visible** can also consume the Mouse key only while the cursor is active, letting the same physical key keep an app shortcut when KeyCursor is hidden.

## Touchpad

Add the **Touchpad** tile to Quick Settings (or use *Show / hide touchpad now* in the app) to turn the screen, or part of it, into a trackpad. The cursor appears with the pad and both go away when you hide it.

| Gesture | Result |
| --- | --- |
| One finger | Move the cursor (relative, with optional acceleration) |
| Tap | Click at the cursor, after the **tap delay** |
| Tap, then touch again and hold or drag | Hold the button and drag. This is a real touch drag, so it scrolls lists. The first tap does not click |
| Tap, then tap again | Double tap: two clicks |
| Two fingers, swipe up / down / left / right | Run the action you assigned to that direction (none by default) |
| Three fingers, swipe up / down / left / right | Run the action you assigned to that direction (Home / Notifications / Back / Recents by default) |

**Tap delay** (0-500 ms, default 150) is how long every tap waits before it clicks. If you touch again inside that time and hold or drag, the tap is dropped, so starting a scroll never leaves a stray click behind. Set it to 0 to click instantly, which turns tap-then-hold off. A tap that turns out to be the start of a two- or three-finger swipe is also dropped.

**Area** can be a *movable pad* (drag the bar at the top to move it, the corner grip to resize it) or the *full screen*. Opacity goes down to 0% on the full-screen pad; the movable pad keeps at least 5% so it can always be found. Keyboard control keeps working while the touchpad is up.

Known limitation: Android documents that injecting a gesture cancels gestures already in progress, including the user's own touch. Clicks are injected after your finger lifts, so they are unaffected, but tap-then-hold and Hold mode inject a touch while a finger may still be on the pad. If drags drop out on your device, turn off *Tap, then hold to drag / scroll*, or use a pad that does not cover the drag target.

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

Other actions include `PAD_TOGGLE` (show / hide the touchpad), `MODE_TOGGLE`, `CURSOR_NEXT`, `LEFT_DOWN`, `LEFT_UP` and `MOVE_*`. `MODE_TOGGLE` switches Normal / Hold.

## Build

Requires JDK 17 and Android SDK 35.

```sh
./gradlew :app:assembleDebug
```

## Notes

Android allows only one enabled Accessibility service to own hardware key filtering at a time. If another accessibility remapper owns that stream, use KeyCursor's external broadcast backend instead.

Cursor artwork derived from Pictogrammers Material Design Icons is listed in `THIRD_PARTY_NOTICES.md`.

KeyCursor itself is MIT licensed. See `PRIVACY.md` for data handling.
