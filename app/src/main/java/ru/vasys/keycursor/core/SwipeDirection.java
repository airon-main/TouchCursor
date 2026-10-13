package ru.vasys.keycursor.core;

/** Direction of a multi-finger swipe on the touchpad (the direction the fingers travel). */
public enum SwipeDirection {
    UP("Swipe up"),
    DOWN("Swipe down"),
    LEFT("Swipe left"),
    RIGHT("Swipe right");

    public final String label;

    SwipeDirection(String label) {
        this.label = label;
    }
}
