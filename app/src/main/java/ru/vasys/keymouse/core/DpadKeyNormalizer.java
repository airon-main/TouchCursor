package ru.vasys.keymouse.core;

import android.view.KeyEvent;
import android.view.Surface;

/**
 * Android may rotate DPAD key codes with the display. Convert the delivered key code
 * back to the physical direction so a keyboard's arrow layout does not change in landscape.
 */
public final class DpadKeyNormalizer {
    private DpadKeyNormalizer() {
    }

    public static int toPhysicalDirection(int keyCode, int displayRotation) {
        switch (displayRotation) {
            case Surface.ROTATION_90:
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return KeyEvent.KEYCODE_DPAD_DOWN;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return KeyEvent.KEYCODE_DPAD_RIGHT;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return KeyEvent.KEYCODE_DPAD_UP;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return KeyEvent.KEYCODE_DPAD_LEFT;
                return keyCode;
            case Surface.ROTATION_180:
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return KeyEvent.KEYCODE_DPAD_UP;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return KeyEvent.KEYCODE_DPAD_LEFT;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return KeyEvent.KEYCODE_DPAD_DOWN;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return KeyEvent.KEYCODE_DPAD_RIGHT;
                return keyCode;
            case Surface.ROTATION_270:
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return KeyEvent.KEYCODE_DPAD_DOWN;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return KeyEvent.KEYCODE_DPAD_RIGHT;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return KeyEvent.KEYCODE_DPAD_UP;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return KeyEvent.KEYCODE_DPAD_LEFT;
                return keyCode;
            default:
                return keyCode;
        }
    }
}
