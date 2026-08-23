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

Hardware `KeyEvent`s are **observed, not consumed**. `MouseAccessibilityService.onKeyEvent()` always returns `false`, including for bound keys, so the original event continues to the rest of Android. Calibration only suppresses KeyMouse's own action while a new bind is being captured.

The exported `ExternalTriggerReceiver` provides system broadcast actions for mouse visibility, mode, cursor cycling, movement and touch down/up.

A held synthetic touch is one logical pointer. `ContinuousTouchController` starts a `StrokeDescription(..., willContinue=true)` and advances it with `continueStroke()`. Releasing creates the final continuation with `willContinue=false`. Scroll mode never loops independent swipe gestures.
