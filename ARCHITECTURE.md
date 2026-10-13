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

## Touchpad

```text
touch on pad window
        ↓
   TouchpadView  ──►  PadGestureEngine (pure Java, no Android types)
                              ↓ listener
                   MouseAccessibilityService
              ┌───────────┼─────────────┐
        PointerController   scrollDirect   performGlobalAction / mode changes
```

`TouchpadOverlay` owns an accessibility-overlay window (full screen or a rectangle stored as screen fractions) that, unlike the cursor overlay, receives touches. `TouchpadView` converts MotionEvents to engine calls and supplies a one-shot `Handler` timer; `PadGestureEngine` recognizes one-finger movement, tap, tap-then-hold drag and two- and three-finger swipes and reports them through a listener, so it can be unit-tested on a plain JVM with a fake timer.

A tap does not click on release. It waits `tapDelayMs`; a second touch inside that window parks the engine in an undecided state, and only then is the outcome chosen: movement past the touch slop or a hold longer than the tap timeout starts a drag (the first tap is discarded), a quick release is a double tap (two clicks, spaced so they are two separate touches), and extra fingers make it a swipe (first tap discarded). The drag is an ordinary held virtual finger, so scrolling comes from the app's own touch handling rather than from Accessibility scroll actions. Two- and three-finger swipes share one implementation: one event per touch once the centroid travels past a threshold, mapped to a configurable `PadAction` per finger count and direction.

Because injected gestures are hit-tested like real touches, a touchable pad above the cursor would swallow KeyCursor's own clicks. `ContinuousTouchController` therefore reports stroke begin/end to the service, which switches the pad window to `FLAG_NOT_TOUCHABLE` for the duration of an injected stroke whose target lies on the pad, and delays the first injected touch by a short settle time so the flag change takes effect first. With the pad hidden, or the cursor outside a rectangular pad, nothing changes.

Once more than one finger has touched, a gesture never falls back to cursor movement when fingers lift; it stays inert until all fingers are up. A touch cancel during tap-and-drag releases the held virtual finger.

Hiding the cursor (long-press of the Mouse key) also hides the pad, so a dead pad can never sit over the screen.

## Mouse modes

- **Normal** — movement only; the click binding produces real touch DOWN / MOVE / UP and can be held for drag.
- **Hold** — keeps one synthetic finger held at the cursor continuously. This is the previous Scroll-mode gesture behavior and uses `StrokeDescription.continueStroke()` while the cursor moves.
- **Scroll** — movement bindings perform Accessibility `ACTION_SCROLL_*` on the scrollable element under the cursor instead of dragging a synthetic finger.

Holding Mouse key shows/hides the cursor. A single short press toggles Normal / Hold; a double press toggles Scroll. Entering Scroll remembers the previous Normal/Hold mode, and the next double press restores it. The single-press mode change is committed after Android's double-tap window so the first tap of a double press cannot briefly inject a Hold touch.

Optionally, the Mouse key itself is consumed only while the cursor is visible. The consume/pass-through decision is fixed on key DOWN and reused through key UP, so a long press that changes cursor visibility cannot leak half of a key sequence to another app. When the cursor is hidden, the Mouse key always passes through.

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
