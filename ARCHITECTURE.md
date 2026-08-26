# Architecture

```text
KeyEvent / broadcast
        ↓
   MouseAction
        ↓
MouseAccessibilityService
        ↓
 PointerController
   ↙       ↓        ↘
Overlay  Touch     Accessibility scroll actions
```

The Accessibility service filters hardware keys, draws the overlay and performs the requested interaction. It does not subscribe to UI accessibility events. Scroll mode retrieves the active node tree only while a scroll direction is held, uses bounds/action metadata to find the scrollable node under the cursor, and performs Android's directional Accessibility scroll action.

The cursor overlay exists only while mouse mode is visible. Cursor position is local to the actual laid-out overlay viewport; touch gestures use that viewport's screen origin plus the same pointer coordinate.

## Mouse modes

- **Normal** — movement only; the click binding produces real touch DOWN / MOVE / UP and can be held for drag.
- **Hold** — keeps one synthetic finger held at the cursor continuously. This is the previous Scroll-mode gesture behavior and uses `StrokeDescription.continueStroke()` while the cursor moves.
- **Scroll** — movement bindings perform Accessibility `ACTION_SCROLL_*` on the scrollable element under the cursor instead of dragging a synthetic finger.

Holding Mouse key shows/hides the cursor. A single short press toggles Normal / Hold; a double press selects Scroll. The single-press mode change is committed after Android's double-tap window so the first tap of a double press cannot briefly inject a Hold touch.

Gesture cancellation clears the active stroke and waits for the next physical movement instead of retrying in a loop.

## Input mapping

Bindings represent physical controls. Scan codes win when available. Key-code-only D-pad bindings are normalized back from Android's display-rotated key codes before matching.

Movement is transformed after binding resolution:

1. optional direction blending;
2. optional landscape rotation with the display;
3. portrait or landscape horizontal inversion;
4. portrait or landscape vertical inversion;
5. final speed normalization.

This keeps calibration independent from orientation preferences. The same resolved direction is used for directional scroll actions.

## Runtime

There is no polling while idle. The 16 ms movement ticker runs only while a movement key is held in Normal/Hold mode. Scroll mode repeats requested Accessibility scroll actions at a slower interval while a direction is held. The overlay is removed from WindowManager when the mouse is hidden.

External broadcast control is component-disabled by default and is enabled only by the user.
