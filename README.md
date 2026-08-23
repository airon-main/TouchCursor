# KeyMouse

Keyboard / D-pad mouse layer for Android 8+.

Package: `ru.vasys.keymouse`

Current version: **0.3.5**.

If Android Studio still tries to launch `dev.keymouse` after updating from an older checkout, run **Sync Project with Gradle Files**, uninstall the old `dev.keymouse` app from the device, then Run again. The current application ID and namespace are both `ru.vasys.keymouse`.

The cursor starts **hidden**:

- **hold Mouse key** — show/hide the cursor;
- **tap Mouse key while visible** — switch **Normal / Scroll**;
- **center cell** — real touch DOWN / MOVE / UP, so hold + movement = drag;
- **Cursor switch** — cycles through enabled cursor styles while the mouse is visible and makes the new style the default;
- **Scroll mode** — keeps one synthetic finger down and moves it continuously instead of generating repeated swipes.
- **Wrap cursor at screen edges** — optional edge wrap; crossing one edge moves the logical cursor to the opposite side;
- **Cursor color** — White / Black / Red / Orange / Yellow / Green / Cyan / Blue / Magenta, with an automatic contrasting outline; settings previews update live.
- **Cursor size** — adjustable from 24–72 dp with a live preview.

## Hardware-key routing

KeyMouse does not globally remap or reserve the keyboard. Routing depends on whether the mouse is active:

- **mouse hidden** — bound keys pass through normally to Android and other apps;
- **mouse visible** — movement, click and cursor-switch bindings are consumed by KeyMouse so Android focus does not move or activate controls in parallel;
- **Mouse key** — remains pass-through for interoperability with Key Mapper and other apps.

During binding calibration KeyMouse temporarily ignores its own action for the key being captured, but it does not disable hardware-key observation globally.

## Bindings

The 3×3 mouse layout uses Android vector drawables, not Unicode / emoji arrows:

```text
NW   N   NE
 W  click  E
SW   S   SE
```

Under the grid are two extra binds:

- **Mouse key** — hold to show/hide, tap to change Normal/Scroll;
- **Cursor switch** — cycle the enabled cursor set while using the mouse.

Binding colors:

- gray — unbound;
- green — built-in default;
- blue — custom binding.

Focus or hover a binding to see its `keyCode`, `KEYCODE_*` name, `scanCode`, and binding state in a short system Toast over the screen. Tap a binding and press a physical key to replace it. Long-press a binding control to clear it.

Default bindings are D-pad Up / Right / Down / Left and D-pad Center. Diagonals, Mouse key and Cursor switch start unbound.

## System feedback

KeyMouse uses short system Toast messages instead of a permanent binding-info row. Toasts appear for binding inspection/capture, mouse show/hide, and Normal/Scroll mode changes.

## Cursor set

**Cursor set** is a collapsible list. Each cursor can be enabled/disabled and one enabled cursor is selected as **Default**.

The stock cursor graphics come from **Pictogrammers Material Design Icons** (Apache-2.0); see `THIRD_PARTY_NOTICES.md`. The set includes Arrow, Hand, Crosshair, Target, Ring, Plus, Corner brackets and Focus. KeyMouse also includes an app-specific **Inward corners** reticle whose four elbows face the hotspot.

Only enabled cursors participate in runtime cycling.

The rendered cursor and synthetic touch share one logical hotspot coordinate; the cursor is drawn inside one fixed full-screen overlay so WindowManager edge clamping cannot move the visual cursor away from the touch point.

## Dark mode

The settings activity follows Android's current day/night configuration. Light mode uses the platform Material light theme; `values-night` switches to the platform Material dark theme automatically. Binding status colors also use brighter variants in night mode.

## Legacy immersive navigation

The cursor overlay exists only while the mouse is visible. While visible, KeyMouse mirrors the legacy `Settings.Global policy_control` navigation-immersive filter for the foreground app so a fullscreen accessibility overlay does not force the navigation bar back on top of an immersive app. The overlay applies the same Android 8.x flags as AOSP `PolicyControl` (`HIDE_NAVIGATION`, `IMMERSIVE_STICKY`, `LAYOUT_HIDE_NAVIGATION`), explicitly avoids `LAYOUT_STABLE`, and reapplies the policy if SystemUI clears it.

For AOSP-style global navigation immersive mode, the documented value is:

```sh
adb shell settings put global policy_control 'immersive.navigation=*'
```

An empty `immersive.navigation=` filter does not match any package in AOSP's `PolicyControl` parser.

## System Intent API

The mouse can be controlled without a physical bind through exported broadcast Intents. This is useful for Key Mapper, Tasker, Automate or ADB.

Direct show/hide event:

```sh
adb shell am broadcast \
  -a ru.vasys.keymouse.action.MOUSE_TOGGLE \
  -n ru.vasys.keymouse/.input.ExternalTriggerReceiver
```

Other aliases:

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
... MOVE_UP_RIGHT / MOVE_RIGHT / etc.
```

For continuous external movement use `ru.vasys.keymouse.input.DOWN` / `UP` with string extra `key=MOVE_LEFT`, etc.

## Build

```sh
gradle :app:assembleDebug
```

Requires JDK 17+ and Android SDK 35. Minimum Android is **8.0 / API 26**. No AndroidX, analytics or network permission.

License: Apache-2.0.
