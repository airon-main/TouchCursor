package dev.keymouse.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import dev.keymouse.core.Binding;
import dev.keymouse.core.MouseAction;
import dev.keymouse.service.MouseAccessibilityService;

public final class MainActivity extends Activity {
    private static final String[] STYLES = {"CROSSHAIR", "ARROW", "PLUS", "RING", "DOT"};
    private static final int[] SPEEDS = {5, 8, 10, 14, 20};

    private dev.keymouse.core.Settings settings;
    private MouseAction captureAction;
    private TextView status;
    private Button styleButton;
    private Button speedButton;
    private final Map<MouseAction, Button> bindingButtons = new EnumMap<>(MouseAction.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new dev.keymouse.core.Settings(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.setCalibrationMode(true);
        refreshButtons();
    }

    @Override
    protected void onPause() {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.setCalibrationMode(false);
        captureAction = null;
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (captureAction != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            MouseAction action = captureAction;
            captureAction = null;
            settings.setBinding(action, event.getKeyCode(), event.getScanCode());
            status.setText("Bound " + label(action) + " to " +
                    new Binding(event.getKeyCode(), event.getScanCode()).displayName());
            refreshButtons();
            notifyService();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("KeyMouse", 26);
        title.setTypeface(null, 1);
        root.addView(title);
        root.addView(text("Real keyboard mouse layer: 8 directions, true hold/drag, continuous touch scrolling, external Intents.", 15));

        status = text("Tap a binding, then press the physical key.", 14);
        status.setTextColor(Color.DKGRAY);
        root.addView(status);

        Button accessibility = button("Open Accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        CheckBox capture = new CheckBox(this);
        capture.setText("Capture hardware keys directly");
        capture.setChecked(settings.captureHardwareKeys());
        capture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setCaptureHardwareKeys(isChecked);
            notifyService();
            if (!isChecked) Toast.makeText(this, "Direct key filtering disabled; use external Intents.", Toast.LENGTH_LONG).show();
        });
        root.addView(capture);

        styleButton = button("");
        styleButton.setOnClickListener(v -> {
            String current = settings.cursorStyle();
            int next = 0;
            for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(current)) next = (i + 1) % STYLES.length;
            settings.setCursorStyle(STYLES[next]);
            styleButton.setText("Cursor: " + STYLES[next]);
            notifyService();
        });
        root.addView(styleButton);

        speedButton = button("");
        speedButton.setOnClickListener(v -> {
            int current = settings.speedPxPerTick();
            int next = 0;
            for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == current) next = (i + 1) % SPEEDS.length;
            settings.setSpeedPxPerTick(SPEEDS[next]);
            speedButton.setText("Speed: " + SPEEDS[next] + " px/tick");
            notifyService();
        });
        root.addView(speedButton);

        TextView heading = text("Bindings", 20);
        heading.setPadding(0, dp(14), 0, dp(4));
        root.addView(heading);

        for (MouseAction action : MouseAction.values()) {
            Button b = button("");
            bindingButtons.put(action, b);
            b.setOnClickListener(v -> {
                captureAction = action;
                status.setText("Press key for: " + label(action));
            });
            b.setOnLongClickListener(v -> {
                settings.clearBinding(action);
                status.setText("Cleared: " + label(action));
                refreshButtons();
                notifyService();
                return true;
            });
            root.addView(b);
        }

        TextView hint = text("Long-press a binding button to clear it. Diagonals also work by holding two cardinal directions together.", 13);
        hint.setTextColor(Color.DKGRAY);
        root.addView(hint);

        refreshButtons();
        return scroll;
    }

    private void refreshButtons() {
        for (MouseAction action : MouseAction.values()) {
            Button b = bindingButtons.get(action);
            if (b != null) b.setText(label(action) + ": " + settings.getBinding(action).displayName());
        }
        if (styleButton != null) styleButton.setText("Cursor: " + settings.cursorStyle());
        if (speedButton != null) speedButton.setText("Speed: " + settings.speedPxPerTick() + " px/tick");
    }

    private void notifyService() {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.reloadSettings();
    }

    private String label(MouseAction action) {
        switch (action) {
            case MOVE_UP: return "↑ Up";
            case MOVE_UP_RIGHT: return "↗ Up-right";
            case MOVE_RIGHT: return "→ Right";
            case MOVE_DOWN_RIGHT: return "↘ Down-right";
            case MOVE_DOWN: return "↓ Down";
            case MOVE_DOWN_LEFT: return "↙ Down-left";
            case MOVE_LEFT: return "← Left";
            case MOVE_UP_LEFT: return "↖ Up-left";
            case LEFT_BUTTON: return "Left mouse / hold";
            case RIGHT_BUTTON: return "Right / context click";
            case TOUCH_MODE: return "Touch-scroll mode";
            case CURSOR_TOGGLE: return "Cursor toggle";
            default: return action.name();
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(48));
        return b;
    }

    private TextView text(String value, int sp) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
