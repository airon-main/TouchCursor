# Changelog

## Unreleased

- Added a touchpad: the whole screen, or a movable/resizable rectangle, becomes a trackpad for the cursor.
- Touchpad gestures: tap to click, tap-then-hold to drag and scroll, double tap for two clicks, and two- and three-finger swipes in four directions, each bound to a configurable action (Back, Home, Recents, Notifications, Quick settings, Power menu, Lock screen, Screenshot, toggle Hold, next cursor, hide touchpad).
- Adjustable tap delay: every tap waits before clicking, so starting a hold-and-drag or a multi-finger swipe never leaves an accidental click.
- Pad opacity can go to 0% on the full-screen pad (the movable pad keeps a 5% minimum).
- Added a Quick Settings tile that shows/hides the touchpad, plus a `PAD_TOGGLE` external broadcast.
- Touchpad settings: sensitivity, pointer acceleration, opacity, natural scrolling, scroll distance, area mode, pad reset.
- Keyboard control is unchanged and keeps working alongside the touchpad.

## 0.6.0 — 2026-08-26

- Added three mouse modes: Normal, Hold and Scroll.
- Single Mouse-key press toggles Normal / Hold; double press toggles Scroll and returns to the previous Normal/Hold mode.
- Scroll mode now performs Android Accessibility scroll actions instead of dragging a synthetic finger.
- Added optional blending of simultaneously held directions into intermediate angles.
- Added optional Mouse-key capture while the cursor is visible, so the same physical key can keep its normal app shortcut while KeyCursor is hidden.
- Reorganized settings: Bindings now appears before Direction, Mouse-key capture lives at the end of Bindings, and portrait/landscape inversion controls are grouped side by side.
- Improved landscape direction handling and cursor configuration.

## 0.5.0 — 2026-08-24

- First public release.
