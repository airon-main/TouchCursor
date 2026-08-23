package dev.keymouse.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import dev.keymouse.core.Binding;
import dev.keymouse.core.BindingState;
import dev.keymouse.core.CursorStyle;
import dev.keymouse.core.MouseAction;
import dev.keymouse.service.MouseAccessibilityService;

public final class MainActivity extends Activity {
    private static final int[] SPEEDS = {5, 8, 10, 14, 20};
    private static final int COLOR_NONE = 0xff777777;
    private static final int COLOR_DEFAULT = 0xff2e7d32;
    private static final int COLOR_CUSTOM = 0xff1565c0;

    private dev.keymouse.core.Settings settings;
    private MouseAction captureAction;
    private int swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private TextView status;
    private Button triggerButton;
    private Button cursorCycleButton;
    private Button speedButton;
    private CheckBox directInput;
    private boolean refreshingCursorRows;

    private final Map<MouseAction, Button> bindingButtons = new EnumMap<>(MouseAction.class);
    private final Map<CursorStyle, CheckBox> cursorChecks = new EnumMap<>(CursorStyle.class);
    private final Map<CursorStyle, RadioButton> cursorDefaults = new EnumMap<>(CursorStyle.class);

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
        refreshAll();
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
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == swallowKeyCode) {
            swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (captureAction != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            MouseAction action = captureAction;
            captureAction = null;
            swallowKeyCode = event.getKeyCode();
            clearDuplicateBinding(action, event.getKeyCode(), event.getScanCode());
            settings.setBinding(action, event.getKeyCode(), event.getScanCode());
            status.setText("Bound " + label(action) + " → " + bindingId(settings.getBinding(action)));
            refreshBindings();
            notifyService();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("KeyMouse", 26);
        title.setTypeface(null, 1);
        root.addView(title);
        root.addView(text("Hold the mouse key to show/hide the cursor. While visible, tap it to switch Normal ↔ Scroll.", 14));

        status = text("Select a cell, then press the physical key.", 13);
        status.setTextColor(Color.DKGRAY);
        root.addView(status);

        Button accessibility = button("Open Accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        TextView bindingsHeading = text("Bindings", 19);
        bindingsHeading.setPadding(0, dp(12), 0, dp(4));
        root.addView(bindingsHeading);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(true);
        LinearLayout.LayoutParams gridOuter = new LinearLayout.LayoutParams(-2, -2);
        gridOuter.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grid, gridOuter);

        addGridButton(grid, MouseAction.MOVE_UP_LEFT, "↖");
        addGridButton(grid, MouseAction.MOVE_UP, "↑");
        addGridButton(grid, MouseAction.MOVE_UP_RIGHT, "↗");
        addGridButton(grid, MouseAction.MOVE_LEFT, "←");
        addGridButton(grid, MouseAction.LEFT_BUTTON, "●");
        addGridButton(grid, MouseAction.MOVE_RIGHT, "→");
        addGridButton(grid, MouseAction.MOVE_DOWN_LEFT, "↙");
        addGridButton(grid, MouseAction.MOVE_DOWN, "↓");
        addGridButton(grid, MouseAction.MOVE_DOWN_RIGHT, "↘");

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.addView(legendItem("none", COLOR_NONE));
        legend.addView(legendItem("default", COLOR_DEFAULT));
        legend.addView(legendItem("custom", COLOR_CUSTOM));
        root.addView(legend);

        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        triggerButton = button("Bind mouse key");
        triggerButton.setOnClickListener(v -> startCapture(MouseAction.TRIGGER));
        triggerButton.setOnLongClickListener(v -> {
            settings.clearBinding(MouseAction.TRIGGER);
            status.setText("Mouse key cleared");
            refreshBindings();
            notifyService();
            return true;
        });
        attachBindingInspector(triggerButton, MouseAction.TRIGGER);
        primary.addView(triggerButton, weighted());

        cursorCycleButton = button("");
        cursorCycleButton.setOnClickListener(v -> {
            CursorStyle next = settings.cycleCursorStyle();
            status.setText("Default cursor: " + next.label);
            refreshCursorRows();
            refreshCursorButton();
            notifyService();
        });
        primary.addView(cursorCycleButton, weighted());
        root.addView(primary);

        TextView triggerHint = text("Mouse key: long press = show/hide cursor; short press while visible = Normal/Scroll.", 12);
        triggerHint.setTextColor(Color.DKGRAY);
        root.addView(triggerHint);

        speedButton = button("");
        speedButton.setOnClickListener(v -> {
            int current = settings.speedPxPerTick();
            int next = SPEEDS[0];
            for (int i = 0; i < SPEEDS.length; i++) {
                if (SPEEDS[i] == current) next = SPEEDS[(i + 1) % SPEEDS.length];
            }
            settings.setSpeedPxPerTick(next);
            refreshSpeedButton();
            notifyService();
        });
        root.addView(speedButton);

        TextView cursorHeading = text("Cursor set", 19);
        cursorHeading.setPadding(0, dp(12), 0, dp(2));
        root.addView(cursorHeading);
        TextView cursorHint = text("Enabled cursors participate in cycling. The selected Default is used when the mouse appears.", 12);
        cursorHint.setTextColor(Color.DKGRAY);
        root.addView(cursorHint);

        for (CursorStyle style : CursorStyle.values()) root.addView(cursorRow(style));

        TextView inputHeading = text("Input backend", 19);
        inputHeading.setPadding(0, dp(12), 0, dp(2));
        root.addView(inputHeading);

        directInput = new CheckBox(this);
        directInput.setText("Direct key input (Accessibility)");
        directInput.setChecked(settings.captureHardwareKeys());
        directInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setCaptureHardwareKeys(isChecked);
            notifyService();
            if (!isChecked) Toast.makeText(this, "KeyMouse will wait for Key Mapper / Intent triggers instead.", Toast.LENGTH_LONG).show();
        });
        root.addView(directInput);
        TextView directHint = text("On: KeyMouse reads the bound hardware keys itself. Off: another remapper owns the keys and sends broadcasts to KeyMouse.", 12);
        directHint.setTextColor(Color.DKGRAY);
        root.addView(directHint);

        TextView footer = text("Tip: focus or hover a binding to see keyCode and scanCode. Long-press a binding cell to clear it.", 12);
        footer.setPadding(0, dp(12), 0, dp(12));
        footer.setTextColor(Color.DKGRAY);
        root.addView(footer);

        refreshAll();
        return scroll;
    }

    private void addGridButton(GridLayout grid, MouseAction action, String symbol) {
        Button b = new Button(this);
        b.setText(symbol);
        b.setTextSize(27);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinHeight(0);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(72);
        lp.height = dp(64);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> startCapture(action));
        b.setOnLongClickListener(v -> {
            settings.clearBinding(action);
            status.setText("Cleared " + label(action));
            refreshBindings();
            notifyService();
            return true;
        });
        attachBindingInspector(b, action);
        bindingButtons.put(action, b);
        grid.addView(b);
    }

    private View cursorRow(CursorStyle style) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        ImageView preview = new ImageView(this);
        preview.setImageResource(style.drawableRes);
        preview.setColorFilter(Color.DKGRAY);
        row.addView(preview, new LinearLayout.LayoutParams(dp(38), dp(38)));

        CheckBox enabled = new CheckBox(this);
        enabled.setText(style.label);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (refreshingCursorRows) return;
            settings.setCursorStyleEnabled(style, isChecked);
            refreshCursorRows();
            refreshCursorButton();
            notifyService();
        });
        cursorChecks.put(style, enabled);
        row.addView(enabled, weighted());

        RadioButton isDefault = new RadioButton(this);
        isDefault.setText("Default");
        isDefault.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (refreshingCursorRows || !isChecked) return;
            settings.setCursorStyle(style);
            refreshCursorRows();
            refreshCursorButton();
            notifyService();
        });
        cursorDefaults.put(style, isDefault);
        row.addView(isDefault);
        return row;
    }

    private void startCapture(MouseAction action) {
        captureAction = action;
        status.setText("Press physical key for " + label(action));
    }

    private void attachBindingInspector(View view, MouseAction action) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && captureAction == null) showBindingInfo(action);
        });
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER && captureAction == null) showBindingInfo(action);
            return false;
        });
    }

    private void showBindingInfo(MouseAction action) {
        BindingState state = settings.bindingState(action);
        status.setText(label(action) + " — " + bindingId(settings.getBinding(action)) + " — " + state.name().toLowerCase());
    }

    private void clearDuplicateBinding(MouseAction target, int keyCode, int scanCode) {
        for (MouseAction action : MouseAction.values()) {
            if (action == target) continue;
            Binding existing = settings.getBinding(action);
            boolean sameScan = scanCode > 0 && existing.scanCode > 0 && scanCode == existing.scanCode;
            boolean sameKey = keyCode >= 0 && existing.keyCode >= 0 && keyCode == existing.keyCode;
            if (sameScan || sameKey) settings.clearBinding(action);
        }
    }

    private void refreshAll() {
        refreshBindings();
        refreshCursorRows();
        refreshCursorButton();
        refreshSpeedButton();
        if (directInput != null) directInput.setChecked(settings.captureHardwareKeys());
    }

    private void refreshBindings() {
        for (Map.Entry<MouseAction, Button> entry : bindingButtons.entrySet()) {
            entry.getValue().setTextColor(bindingColor(settings.bindingState(entry.getKey())));
        }
        if (triggerButton != null) {
            BindingState state = settings.bindingState(MouseAction.TRIGGER);
            triggerButton.setText("Bind mouse key" + (state == BindingState.NONE ? "" : " •"));
            triggerButton.setTextColor(bindingColor(state));
        }
    }

    private void refreshCursorRows() {
        refreshingCursorRows = true;
        CursorStyle selected = settings.cursorStyle();
        for (CursorStyle style : CursorStyle.values()) {
            CheckBox check = cursorChecks.get(style);
            RadioButton radio = cursorDefaults.get(style);
            if (check != null) check.setChecked(settings.isCursorStyleEnabled(style));
            if (radio != null) {
                radio.setEnabled(settings.isCursorStyleEnabled(style));
                radio.setChecked(style == selected);
            }
        }
        refreshingCursorRows = false;
    }

    private void refreshCursorButton() {
        if (cursorCycleButton != null) cursorCycleButton.setText("Cursor: " + settings.cursorStyle().label + " ›");
    }

    private void refreshSpeedButton() {
        if (speedButton != null) speedButton.setText("Speed: " + settings.speedPxPerTick() + " px/tick");
    }

    private void notifyService() {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.reloadSettings();
    }

    private String bindingId(Binding binding) {
        if (!binding.isBound()) return "unbound";
        String keyName = binding.keyCode >= 0 ? KeyEvent.keyCodeToString(binding.keyCode) : "KEYCODE_UNKNOWN";
        return "keyCode=" + binding.keyCode + " (" + keyName + "), scanCode=" + binding.scanCode;
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
            case LEFT_BUTTON: return "● Click / hold";
            case TRIGGER: return "Mouse key";
            default: return action.name();
        }
    }

    private int bindingColor(BindingState state) {
        switch (state) {
            case DEFAULT: return COLOR_DEFAULT;
            case CUSTOM: return COLOR_CUSTOM;
            case NONE:
            default: return COLOR_NONE;
        }
    }

    private TextView legendItem(String value, int color) {
        TextView t = text("● " + value + "   ", 12);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
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
