# Architecture

```text
hardware KeyEvent / system broadcast Intent
                    ↓
                 MouseAction
                    ↓
          MouseAccessibilityService
             ↙               ↘
       trigger state       pressed directions
             ↓                    ↓
                    PointerController
                     ↙          ↘
               CursorOverlay   ContinuousTouchController
```

The cursor starts hidden:

- long Mouse-key press toggles cursor visibility;
- short Mouse-key press while visible toggles Normal / Scroll;
- Cursor-switch press cycles the enabled cursor set and updates the default.

Hardware `KeyEvent`s use session-based routing. While the mouse is hidden, bound keys pass through unchanged. While the mouse is visible, movement, click and cursor-switch events are consumed after KeyMouse handles them so Android focus navigation cannot run in parallel. The Mouse-key trigger remains pass-through on both DOWN and UP for interoperability. Calibration only suppresses KeyMouse's own action while a new bind is being captured.

The exported `ExternalTriggerReceiver` provides system broadcast actions for mouse visibility, mode, cursor cycling, movement and touch down/up.

A held synthetic touch is one logical pointer. `ContinuousTouchController` starts a `StrokeDescription(..., willContinue=true)` and advances it with `continueStroke()`. Releasing creates the final continuation with `willContinue=false`. Scroll mode never loops independent swipe gestures.

`CursorOverlay` is one fixed full-screen accessibility overlay. The app keeps exactly one logical hotspot `(x, y)`; the cursor child view is rendered around that coordinate and gesture injection uses the same coordinate directly. This avoids WindowManager placement latency/clamping causing visual/touch drift at screen edges.

Optional edge wrapping is applied to the logical hotspot. If a wrap occurs while a synthetic finger is already down, `ContinuousTouchController` rebases with a short release + press at the opposite edge rather than drawing an unintended full-screen line.


## Overlay lifetime and immersive navigation

`CursorOverlay` is attached to WindowManager only while the mouse is visible. It is removed completely when hidden. Because `TYPE_ACCESSIBILITY_OVERLAY` is not an application-window type, `ImmersiveNavigationPolicy` mirrors legacy `Settings.Global.policy_control` navigation filters onto the overlay using the last foreground package reported by accessibility window events. The overlay mirrors Android 8.x `PolicyControl` system-UI flags exactly and re-applies them after SystemUI visibility resets.

## Appearance

Cursor style, enabled cursor set, cursor color and cursor size are independent settings. Cursor tint/size are applied at runtime and previewed live in settings; a contrasting outline is selected automatically. The settings Activity uses resource-qualified light/dark platform themes (`values` / `values-night`).
