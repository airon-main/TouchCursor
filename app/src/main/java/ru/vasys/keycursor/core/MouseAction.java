package ru.vasys.keycursor.core;

public enum MouseAction {
    MOVE_UP(0, -1, true),
    MOVE_UP_RIGHT(1, -1, true),
    MOVE_RIGHT(1, 0, true),
    MOVE_DOWN_RIGHT(1, 1, true),
    MOVE_DOWN(0, 1, true),
    MOVE_DOWN_LEFT(-1, 1, true),
    MOVE_LEFT(-1, 0, true),
    MOVE_UP_LEFT(-1, -1, true),
    LEFT_BUTTON(0, 0, false),
    TRIGGER(0, 0, false),
    CURSOR_NEXT(0, 0, false);

    public final int dx;
    public final int dy;
    public final boolean movement;

    MouseAction(int dx, int dy, boolean movement) {
        this.dx = dx;
        this.dy = dy;
        this.movement = movement;
    }
}
