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

Android may rotate the four `KEYCODE_DPAD_*` values with display orientation. Calibrated scan-code bindings are matched first; key-code-only bindings are inverse-rotated back to the physical D-pad direction before matching, so the keyboard layout stays stable in landscape.

Hardware `KeyEvent`s use session-based routing. While the mouse is hidden, bound keys pass through unchanged. While the mouse is visible, movement, click and cursor-switch events are consumed after KeyMouse handles them so Android focus navigation cannot run in parallel. The Mouse-key trigger remains pass-through on both DOWN and UP for normal application interoperability. Android itself allows only one Accessibility service to own key filtering at a time, so other accessibility remappers may require the external broadcast backend. Calibration only suppresses KeyMouse's own action while a new bind is being captured.

The exported `ExternalTriggerReceiver` provides system broadcast actions for mouse visibility, mode, cursor cycling, movement and touch down/up. It is disabled at the component level by default and is enabled only after the user opts into external control in settings.

A held synthetic touch is one logical pointer. `ContinuousTouchController` starts a `StrokeDescription(..., willContinue=true)` and advances it with `continueStroke()`. Releasing creates the final continuation with `willContinue=false`. Scroll mode never loops independent swipe gestures.

`CursorOverlay` is one fixed accessibility overlay while mouse mode is visible. The logical hotspot `(x, y)` is local to the overlay's actual laid-out viewport; injected gesture coordinates are `viewport screen origin + hotspot`. This keeps visual and touch coordinates aligned when OEM landscape layouts offset or shrink overlays around system bars.

Optional edge wrapping is applied to the logical hotspot. If a wrap occurs while a synthetic finger is already down, `ContinuousTouchController` rebases with a short release + press at the opposite edge rather than drawing an unintended full-screen line.


## Overlay lifetime and idle behavior

`CursorOverlay` is attached to WindowManager only while the mouse is visible and removed completely when hidden. The actual overlay viewport geometry is refreshed from layout/configuration changes; movement does not query WindowManager every frame. The Accessibility service does not subscribe to UI accessibility events or inspect window content.

## Appearance

Cursor style, enabled cursor set, cursor color and cursor size are independent settings. Cursor tint/size are applied at runtime and previewed live in settings through the same shared vector renderer. Every cursor gets a thin inverse-RGB outline for visibility on mixed backgrounds. The settings Activity uses resource-qualified light/dark platform themes (`values` / `values-night`).


## Gesture cancellation

A cancelled synthetic gesture is not immediately restarted in a loop. The controller clears the active stroke and waits for the next physical movement before lazily starting a new continued stroke. This avoids fighting real touch input, another accessibility service or SystemUI and prevents cancellation retry loops.
