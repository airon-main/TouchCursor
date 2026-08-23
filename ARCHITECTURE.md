# Architecture

```text
KeyEvent / broadcast
        ↓
   MouseAction
        ↓
MouseAccessibilityService
        ↓
 PointerController
   ↙          ↘
CursorOverlay  ContinuousTouchController
```

The Accessibility service is used only for hardware-key filtering and gesture injection. It does not inspect the UI tree or subscribe to accessibility events.

The cursor overlay exists only while mouse mode is visible. Cursor position is local to the actual laid-out overlay viewport; touch gestures use that viewport's screen origin plus the same pointer coordinate.

A held click or Scroll mode uses one continued synthetic pointer through `StrokeDescription.continueStroke()`. Gesture cancellation clears the active stroke and waits for the next physical movement instead of retrying in a loop.

## Input mapping

Bindings represent physical controls. Scan codes win when available. Key-code-only D-pad bindings are normalized back from Android's display-rotated key codes before matching.

Movement is transformed after binding resolution:

1. optional landscape rotation with the display;
2. portrait or landscape horizontal inversion;
3. portrait or landscape vertical inversion;
4. diagonal speed normalization.

This keeps calibration independent from orientation preferences.

## Runtime

There is no polling while idle. The 16 ms movement ticker runs only while a movement key is held. The overlay is removed from WindowManager when the mouse is hidden.

External broadcast control is component-disabled by default and is enabled only by the user.
