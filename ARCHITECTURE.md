# Architecture

`KeyEvent / Intent -> MouseAction -> pressed-action state -> PointerController -> CursorOverlay + ContinuousTouchController`

The important invariant is that a held touch is represented by one logical pointer. `ContinuousTouchController` starts a `StrokeDescription(..., willContinue=true)` and advances it with `continueStroke()`. Releasing creates the final continuation with `willContinue=false`.

No component generates a loop of independent swipe gestures for scrolling.
