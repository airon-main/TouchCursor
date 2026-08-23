# Architecture

```text
hardware KeyEvent / external Intent
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

The cursor starts hidden. The trigger key is the only binding consumed while hidden:

- long press toggles cursor visibility;
- short press while visible toggles Normal / Scroll.

When hidden, movement/click key events are returned to Android instead of being swallowed.

A held synthetic touch is one logical pointer. `ContinuousTouchController` starts a `StrokeDescription(..., willContinue=true)` and advances it with `continueStroke()`. Releasing creates the final continuation with `willContinue=false`. Scroll mode never loops independent swipe gestures.
