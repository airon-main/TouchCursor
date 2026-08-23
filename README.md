# KeyMouse

Keyboard/D-pad mouse layer for Android 8+.

The cursor is **hidden by default**. Bind one **Mouse key**:

- **hold Mouse key** — show/hide the cursor;
- **tap Mouse key while cursor is visible** — switch **Normal ↔ Scroll**;
- **center cell** — real touch DOWN / MOVE / UP, so hold + arrows = drag;
- **Scroll mode** — keeps one synthetic finger down and moves it continuously. No repeated-swipe spam.

When the cursor is hidden, bound D-pad keys are not consumed, so the phone keeps normal keypad navigation.

## Bindings

The 3×3 grid is the mouse layout:

```text
↖  ↑  ↗
←  ●  →
↙  ↓  ↘
```

Binding color:

- gray — unbound;
- green — built-in default;
- blue — custom binding.

Focus/hover a cell to see its `keyCode` and `scanCode`. Tap a cell and press a physical key to rebind it. Long-press a cell to clear it.

Default bindings are D-pad ↑ → ↓ ← and D-pad center. Diagonals and Mouse key start unbound.

## Cursors

Cursor shapes come from **Pictogrammers Material Design Icons** (Apache-2.0), not hand-drawn placeholders. Enable/disable shapes in **Cursor set** and choose the default. The **Cursor** button cycles only through enabled shapes and makes the chosen one the default.

See `THIRD_PARTY_NOTICES.md`.

## Direct input vs Key Mapper

**Direct key input (Accessibility)** means KeyMouse itself listens for the bound hardware keys.

Turn it off if Key Mapper / Tasker / another remapper should own the physical keys. KeyMouse can then be controlled with broadcasts.

Useful no-extra aliases:

```text
dev.keymouse.action.TRIGGER_SHORT
dev.keymouse.action.TRIGGER_LONG
dev.keymouse.action.TRIGGER_DOWN
dev.keymouse.action.TRIGGER_UP
dev.keymouse.action.LEFT_DOWN
dev.keymouse.action.LEFT_UP
dev.keymouse.action.MOVE_UP
... MOVE_UP_RIGHT / MOVE_RIGHT / etc.
```

Receiver component:

```text
dev.keymouse/dev.keymouse.input.ExternalTriggerReceiver
```

For continuous external movement use `dev.keymouse.input.DOWN` / `UP` with string extra `key=MOVE_LEFT` etc.

## Build

```sh
gradle :app:assembleDebug
```

Requires JDK 17+ and Android SDK 35. Minimum Android is **8.0 / API 26**. No AndroidX, analytics or network permission.

License: Apache-2.0.
