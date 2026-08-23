package ru.vasys.keymouse.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import ru.vasys.keymouse.R;
import ru.vasys.keymouse.core.Binding;
import ru.vasys.keymouse.core.BindingState;
import ru.vasys.keymouse.core.CursorColor;
import ru.vasys.keymouse.core.CursorStyle;
import ru.vasys.keymouse.core.MouseAction;
import ru.vasys.keymouse.input.ExternalTriggerReceiver;
import ru.vasys.keymouse.service.MouseAccessibilityService;

public final class MainActivity extends Activity {
    private static final int[] SPEEDS = {5, 8, 10, 14, 20};

    private ru.vasys.keymouse.core.Settings settings;
    private MouseAction captureAction;
    private int swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private Toast feedbackToast;
    private Button triggerButton;
    private Button cursorSwitchButton;
    private Button speedButton;
    private ImageView cursorAppearancePreview;
    private CheckBox externalControlCheck;
    private CheckBox wrapEdgesCheck;
    private Spinner cursorColorSpinner;
    private TextView cursorSizeLabel;
    private SeekBar cursorSizeSeek;
    private Button cursorSetToggleButton;
    private LinearLayout cursorListContainer;
    private boolean cursorSetExpanded;
    private boolean refreshingCursorRows;
    private boolean refreshingCursorColor;
    private boolean cursorSizeTracking;
    private int pendingCursorSizeDp;

    private final Map<MouseAction, ImageButton> bindingButtons = new EnumMap<>(MouseAction.class);
    private final Map<CursorStyle, CheckBox> cursorChecks = new EnumMap<>(CursorStyle.class);
    private final Map<CursorStyle, RadioButton> cursorDefaults = new EnumMap<>(CursorStyle.class);
    private final Map<CursorStyle, ImageView> cursorPreviews = new EnumMap<>(CursorStyle.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new ru.vasys.keymouse.core.Settings(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    protected void onPause() {
        setServiceCalibration(false);
        captureAction = null;
        swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = null;
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == swallowKeyCode) {
            swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            setServiceCalibration(false);
            return true;
        }
        if (captureAction != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            MouseAction action = captureAction;
            captureAction = null;
            swallowKeyCode = event.getKeyCode();
            clearDuplicateBinding(action, event.getKeyCode(), event.getScanCode());
            settings.setBinding(action, event.getKeyCode(), event.getScanCode());
            showBindingToast(action, "Bound");
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

        root.addView(sectionHeading("Accessibility"));
        root.addView(sectionDescription("Required to read hardware keys, draw the cursor overlay and inject touch gestures."));

        Button accessibility = button("Open Accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        externalControlCheck = new CheckBox(this);
        externalControlCheck.setText("Allow external control (Key Mapper / Tasker)");
        externalControlCheck.setPadding(0, dp(4), 0, dp(4));
        externalControlCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.externalControlEnabled() == isChecked) return;
            settings.setExternalControlEnabled(isChecked);
            ExternalTriggerReceiver.setComponentEnabled(this, isChecked);
        });
        root.addView(externalControlCheck);
        root.addView(sectionDescription("Off by default. Enable only if another app should control KeyMouse through broadcast Intents."));

        root.addView(sectionHeading("Pointer"));
        root.addView(sectionDescription("Tune cursor movement and appearance. Preview updates immediately."));

        cursorAppearancePreview = new ImageView(this);
        cursorAppearancePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams appearancePreviewParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        appearancePreviewParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(cursorAppearancePreview, appearancePreviewParams);

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
        speedButton.setPadding(0, dp(6), 0, 0);
        root.addView(speedButton);

        wrapEdgesCheck = new CheckBox(this);
        wrapEdgesCheck.setText("Wrap cursor at screen edges");
        wrapEdgesCheck.setPadding(0, dp(4), 0, dp(4));
        wrapEdgesCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.wrapEdges() == isChecked) return;
            settings.setWrapEdges(isChecked);
            notifyService();
        });
        root.addView(wrapEdgesCheck);

        TextView colorLabel = text("Cursor color", 14);
        colorLabel.setTypeface(null, 1);
        colorLabel.setPadding(0, dp(8), 0, dp(2));
        root.addView(colorLabel);

        cursorColorSpinner = new Spinner(this);
        String[] colorLabels = new String[CursorColor.values().length];
        for (int i = 0; i < CursorColor.values().length; i++) colorLabels[i] = CursorColor.values()[i].label;
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, colorLabels);
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cursorColorSpinner.setAdapter(colorAdapter);
        CursorColor initialColor = settings.cursorColor();
        int initialColorPosition = 0;
        for (int i = 0; i < CursorColor.values().length; i++) {
            if (CursorColor.values()[i] == initialColor) initialColorPosition = i;
        }
        cursorColorSpinner.setSelection(initialColorPosition, false);
        cursorColorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshingCursorColor) return;
                CursorColor selected = CursorColor.values()[position];
                if (selected == settings.cursorColor()) return;
                settings.setCursorColor(selected);
                refreshCursorPreviews();
                notifyService();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(cursorColorSpinner);

        cursorSizeLabel = text("", 14);
        cursorSizeLabel.setTypeface(null, 1);
        cursorSizeLabel.setPadding(0, dp(10), 0, 0);
        root.addView(cursorSizeLabel);

        cursorSizeSeek = new SeekBar(this);
        cursorSizeSeek.setMax(ru.vasys.keymouse.core.Settings.MAX_CURSOR_SIZE_DP - ru.vasys.keymouse.core.Settings.MIN_CURSOR_SIZE_DP);
        cursorSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int value = ru.vasys.keymouse.core.Settings.MIN_CURSOR_SIZE_DP + progress;
                pendingCursorSizeDp = value;
                refreshCursorSizeLabel(value);
                refreshCursorPreviews(value);
                previewCursorSize(value);
                // Keyboard/D-pad SeekBar changes do not have a touch tracking lifecycle.
                if (!cursorSizeTracking) {
                    settings.setCursorSizeDp(value);
                    notifyService();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                cursorSizeTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                cursorSizeTracking = false;
                settings.setCursorSizeDp(pendingCursorSizeDp);
                notifyService();
            }
        });
        root.addView(cursorSizeSeek);

        cursorSetToggleButton = button("");
        cursorSetToggleButton.setPadding(0, dp(8), 0, 0);
        cursorSetToggleButton.setOnClickListener(v -> {
            cursorSetExpanded = !cursorSetExpanded;
            refreshCursorSetVisibility();
        });
        root.addView(cursorSetToggleButton);

        cursorListContainer = new LinearLayout(this);
        cursorListContainer.setOrientation(LinearLayout.VERTICAL);
        TextView cursorHint = sectionDescription("Enable cursor styles for cycling and choose the one used by default.");
        cursorListContainer.addView(cursorHint);
        for (CursorStyle style : CursorStyle.values()) cursorListContainer.addView(cursorRow(style));
        root.addView(cursorListContainer);

        root.addView(sectionHeading("Bindings"));
        TextView triggerHint = sectionDescription("Hold Mouse key: show/hide. Tap it while visible: Normal/Scroll. Cursor switch: next enabled cursor.");
        triggerHint.setPadding(0, 0, 0, dp(8));
        root.addView(triggerHint);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(true);
        LinearLayout.LayoutParams gridOuter = new LinearLayout.LayoutParams(-2, -2);
        gridOuter.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grid, gridOuter);

        addGridButton(grid, MouseAction.MOVE_UP_LEFT, R.drawable.ic_binding_arrow_nw);
        addGridButton(grid, MouseAction.MOVE_UP, R.drawable.ic_binding_arrow_n);
        addGridButton(grid, MouseAction.MOVE_UP_RIGHT, R.drawable.ic_binding_arrow_ne);
        addGridButton(grid, MouseAction.MOVE_LEFT, R.drawable.ic_binding_arrow_w);
        addGridButton(grid, MouseAction.LEFT_BUTTON, R.drawable.ic_binding_click);
        addGridButton(grid, MouseAction.MOVE_RIGHT, R.drawable.ic_binding_arrow_e);
        addGridButton(grid, MouseAction.MOVE_DOWN_LEFT, R.drawable.ic_binding_arrow_sw);
        addGridButton(grid, MouseAction.MOVE_DOWN, R.drawable.ic_binding_arrow_s);
        addGridButton(grid, MouseAction.MOVE_DOWN_RIGHT, R.drawable.ic_binding_arrow_se);

        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        primary.setPadding(0, dp(6), 0, 0);

        triggerButton = bindingButton("Bind Mouse key", MouseAction.TRIGGER);
        primary.addView(triggerButton, weighted());

        cursorSwitchButton = bindingButton("Bind cursor switch", MouseAction.CURSOR_NEXT);
        primary.addView(cursorSwitchButton, weighted());
        root.addView(primary);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.addView(legendItem("unbound", bindingColor(BindingState.NONE)));
        legend.addView(legendItem("default", bindingColor(BindingState.DEFAULT)));
        legend.addView(legendItem("custom", bindingColor(BindingState.CUSTOM)));
        root.addView(legend);

        TextView inputNote = sectionDescription("When the mouse is hidden, bound keys pass through normally. While visible, movement, click and cursor-switch are captured so Android focus stays still. Mouse key remains pass-through.");
        inputNote.setPadding(0, dp(10), 0, dp(4));
        root.addView(inputNote);

        TextView footer = sectionDescription("Tap to bind. Long-press to clear. Focus or hover a binding to show its key ID in a system Toast.");
        footer.setPadding(0, dp(2), 0, dp(12));
        root.addView(footer);

        refreshAll();
        return scroll;
    }

    private void addGridButton(GridLayout grid, MouseAction action, int iconRes) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(iconRes);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(16), dp(16), dp(16), dp(16));
        b.setContentDescription(label(action));
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(72);
        lp.height = dp(64);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> startCapture(action));
        b.setOnLongClickListener(v -> {
            settings.clearBinding(action);
            showBindingToast(action, "Cleared");
            refreshBindings();
            notifyService();
            return true;
        });
        attachBindingInspector(b, action);
        bindingButtons.put(action, b);
        grid.addView(b);
    }

    private Button bindingButton(String title, MouseAction action) {
        Button b = button(title);
        b.setOnClickListener(v -> startCapture(action));
        b.setOnLongClickListener(v -> {
            settings.clearBinding(action);
            showBindingToast(action, "Cleared");
            refreshBindings();
            notifyService();
            return true;
        });
        attachBindingInspector(b, action);
        return b;
    }

    private View cursorRow(CursorStyle style) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        ImageView preview = new ImageView(this);
        preview.setImageResource(style.drawableRes);
        cursorPreviews.put(style, preview);
        row.addView(preview, new LinearLayout.LayoutParams(dp(38), dp(38)));

        CheckBox enabled = new CheckBox(this);
        enabled.setText(style.label);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (refreshingCursorRows) return;
            settings.setCursorStyleEnabled(style, isChecked);
            refreshCursorRows();
            refreshCursorPreviews();
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
            refreshCursorPreviews();
            notifyService();
        });
        cursorDefaults.put(style, isDefault);
        row.addView(isDefault);
        return row;
    }

    private void startCapture(MouseAction action) {
        captureAction = action;
        swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        setServiceCalibration(true);
        showToast("Bind " + label(action) + ": press a physical key…");
    }

    private void attachBindingInspector(View view, MouseAction action) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && captureAction == null) showBindingToast(action, null);
        });
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER && captureAction == null) showBindingToast(action, null);
            return false;
        });
    }

    private void showBindingToast(MouseAction action, String prefix) {
        BindingState state = settings.bindingState(action);
        String value = label(action) + " · " + bindingId(settings.getBinding(action)) + " · " + state.name().toLowerCase();
        showToast(prefix == null ? value : prefix + ": " + value);
    }

    private void showToast(String message) {
        if (feedbackToast != null) feedbackToast.cancel();
        feedbackToast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        feedbackToast.show();
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
        refreshExternalControl();
        refreshCursorRows();
        refreshCursorSetVisibility();
        refreshSpeedButton();
        refreshWrapEdges();
        refreshCursorColor();
        refreshCursorSize();
        refreshCursorPreviews();
    }

    private void refreshBindings() {
        for (Map.Entry<MouseAction, ImageButton> entry : bindingButtons.entrySet()) {
            entry.getValue().setColorFilter(bindingColor(settings.bindingState(entry.getKey())));
        }
        if (triggerButton != null) {
            BindingState state = settings.bindingState(MouseAction.TRIGGER);
            triggerButton.setText("Bind Mouse key" + (state == BindingState.NONE ? "" : "  •"));
            triggerButton.setTextColor(bindingColor(state));
        }
        if (cursorSwitchButton != null) {
            BindingState state = settings.bindingState(MouseAction.CURSOR_NEXT);
            cursorSwitchButton.setText("Bind cursor switch" + (state == BindingState.NONE ? "" : "  •"));
            cursorSwitchButton.setTextColor(bindingColor(state));
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

    private void refreshCursorSetVisibility() {
        if (cursorSetToggleButton != null) cursorSetToggleButton.setText(cursorSetExpanded ? "Cursor set  ▾" : "Cursor set  ▸");
        if (cursorListContainer != null) cursorListContainer.setVisibility(cursorSetExpanded ? View.VISIBLE : View.GONE);
    }

    private void refreshSpeedButton() {
        if (speedButton != null) speedButton.setText("Cursor speed: " + settings.speedPxPerTick());
    }

    private void refreshExternalControl() {
        boolean enabled = settings.externalControlEnabled();
        ExternalTriggerReceiver.setComponentEnabled(this, enabled);
        if (externalControlCheck != null) externalControlCheck.setChecked(enabled);
    }

    private void refreshWrapEdges() {
        if (wrapEdgesCheck != null) wrapEdgesCheck.setChecked(settings.wrapEdges());
    }

    private void refreshCursorColor() {
        if (cursorColorSpinner == null) return;
        CursorColor selected = settings.cursorColor();
        CursorColor[] all = CursorColor.values();
        int position = 0;
        for (int i = 0; i < all.length; i++) if (all[i] == selected) position = i;
        refreshingCursorColor = true;
        cursorColorSpinner.setSelection(position, false);
        refreshingCursorColor = false;
    }

    private void refreshCursorSize() {
        int value = settings.cursorSizeDp();
        pendingCursorSizeDp = value;
        refreshCursorSizeLabel(value);
        if (cursorSizeSeek != null) {
            int progress = value - ru.vasys.keymouse.core.Settings.MIN_CURSOR_SIZE_DP;
            cursorSizeSeek.setProgress(progress);
        }
    }

    private void refreshCursorSizeLabel(int value) {
        if (cursorSizeLabel != null) cursorSizeLabel.setText("Cursor size: " + value + " dp");
    }

    private void refreshCursorPreviews() {
        refreshCursorPreviews(settings.cursorSizeDp());
    }

    private void refreshCursorPreviews(int sizeDp) {
        int size = dp(sizeDp);
        int tint = settings.cursorColor().argb;
        if (cursorAppearancePreview != null) {
            cursorAppearancePreview.setImageResource(settings.cursorStyle().drawableRes);
            cursorAppearancePreview.setColorFilter(tint);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cursorAppearancePreview.getLayoutParams();
            lp.width = size;
            lp.height = size;
            cursorAppearancePreview.setLayoutParams(lp);
        }
        for (Map.Entry<CursorStyle, ImageView> entry : cursorPreviews.entrySet()) {
            ImageView preview = entry.getValue();
            preview.setColorFilter(tint);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) preview.getLayoutParams();
            lp.width = size;
            lp.height = size;
            preview.setLayoutParams(lp);
        }
    }

    private void previewCursorSize(int sizeDp) {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.previewCursorSizeDp(sizeDp);
    }

    private void notifyService() {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.reloadSettings();
    }

    private void setServiceCalibration(boolean value) {
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service != null) service.setCalibrationMode(value);
    }

    private String bindingId(Binding binding) {
        if (!binding.isBound()) return "unbound";
        String keyName = binding.keyCode >= 0 ? KeyEvent.keyCodeToString(binding.keyCode) : "KEYCODE_UNKNOWN";
        return "keyCode=" + binding.keyCode + " (" + keyName + "), scanCode=" + binding.scanCode;
    }

    private String label(MouseAction action) {
        switch (action) {
            case MOVE_UP: return "Up";
            case MOVE_UP_RIGHT: return "Up-right";
            case MOVE_RIGHT: return "Right";
            case MOVE_DOWN_RIGHT: return "Down-right";
            case MOVE_DOWN: return "Down";
            case MOVE_DOWN_LEFT: return "Down-left";
            case MOVE_LEFT: return "Left";
            case MOVE_UP_LEFT: return "Up-left";
            case LEFT_BUTTON: return "Click / hold";
            case TRIGGER: return "Mouse key";
            case CURSOR_NEXT: return "Cursor switch";
            default: return action.name();
        }
    }

    private int bindingColor(BindingState state) {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        switch (state) {
            case DEFAULT: return night ? 0xff81c784 : 0xff2e7d32;
            case CUSTOM: return night ? 0xff64b5f6 : 0xff1565c0;
            case NONE:
            default: return night ? 0xffb0bec5 : 0xff777777;
        }
    }

    private TextView legendItem(String value, int color) {
        TextView t = text(value + "   ", 12);
        t.setTextColor(color);
        return t;
    }

    private TextView sectionHeading(String value) {
        TextView t = text(value, 19);
        t.setTypeface(null, 1);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private TextView sectionDescription(String value) {
        TextView t = text(value, 12);
        t.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
        t.setPadding(0, 0, 0, dp(6));
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

    private int themeColor(int attr, int fallback) {
        TypedArray a = obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, fallback);
        } finally {
            a.recycle();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
