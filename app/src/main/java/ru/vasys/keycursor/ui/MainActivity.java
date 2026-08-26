package ru.vasys.keycursor.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import ru.vasys.keycursor.R;
import ru.vasys.keycursor.core.Binding;
import ru.vasys.keycursor.core.BindingState;
import ru.vasys.keycursor.core.CursorColor;
import ru.vasys.keycursor.core.CursorStyle;
import ru.vasys.keycursor.core.MouseAction;
import ru.vasys.keycursor.input.ExternalTriggerReceiver;
import ru.vasys.keycursor.service.MouseAccessibilityService;
import ru.vasys.keycursor.view.CursorGlyphView;

public final class MainActivity extends Activity {
    private static final int[] SPEEDS = {5, 8, 10, 14, 20};

    private ru.vasys.keycursor.core.Settings settings;
    private MouseAction captureAction;
    private int swallowKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private Toast feedbackToast;
    private Button triggerButton;
    private Button cursorSwitchButton;
    private Button speedButton;
    private CursorGlyphView cursorAppearancePreview;
    private Switch externalControlCheck;
    private Switch wrapEdgesCheck;
    private Switch rotateLandscapeCheck;
    private Switch invertHorizontalPortraitCheck;
    private Switch invertVerticalPortraitCheck;
    private Switch invertHorizontalLandscapeCheck;
    private Switch invertVerticalLandscapeCheck;
    private Switch blendDirectionsCheck;
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
    private final Map<CursorStyle, CursorGlyphView> cursorPreviews = new EnumMap<>(CursorStyle.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new ru.vasys.keycursor.core.Settings(this);
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
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(pageBackgroundColor());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("KeyCursor", 28);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(2), 0, 0, 0);
        root.addView(title);

        TextView subtitle = sectionDescription("Keyboard controls, real touch gestures.");
        subtitle.setTextSize(13);
        subtitle.setPadding(dp(2), 0, 0, dp(8));
        root.addView(subtitle);

        root.addView(buildAccessibilityCard());
        root.addView(buildPointerCard());
        root.addView(buildDirectionCard());
        root.addView(buildBindingsCard());
        root.addView(buildExternalControlCard());

        refreshAll();
        return scroll;
    }

    private View buildAccessibilityCard() {
        LinearLayout card = card(
                "Accessibility",
                "Required for hardware keys, the cursor overlay and touch injection.");
        Button accessibility = button("Open Accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        card.addView(accessibility);
        return card;
    }

    private View buildPointerCard() {
        LinearLayout card = card(
                "Pointer",
                "Movement, appearance and edge behavior. Changes are previewed immediately.");

        cursorAppearancePreview = new CursorGlyphView(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        previewParams.bottomMargin = dp(8);
        card.addView(cursorAppearancePreview, previewParams);

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
        card.addView(settingRow("Movement speed", speedButton));

        cursorSizeLabel = settingLabel("");
        card.addView(cursorSizeLabel);
        cursorSizeSeek = buildCursorSizeSeek();
        card.addView(cursorSizeSeek);

        card.addView(settingLabel("Cursor color"));
        cursorColorSpinner = buildCursorColorSpinner();
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, -2);
        spinnerParams.bottomMargin = dp(4);
        card.addView(cursorColorSpinner, spinnerParams);

        wrapEdgesCheck = optionSwitch("Wrap at screen edges");
        wrapEdgesCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.wrapEdges() == isChecked) return;
            settings.setWrapEdges(isChecked);
            notifyService();
        });
        card.addView(wrapEdgesCheck);

        cursorSetToggleButton = button("");
        LinearLayout.LayoutParams cursorSetButtonParams = new LinearLayout.LayoutParams(-1, -2);
        cursorSetButtonParams.topMargin = dp(8);
        cursorSetToggleButton.setLayoutParams(cursorSetButtonParams);
        cursorSetToggleButton.setOnClickListener(v -> {
            cursorSetExpanded = !cursorSetExpanded;
            refreshCursorSetVisibility();
        });
        card.addView(cursorSetToggleButton);

        cursorListContainer = new LinearLayout(this);
        cursorListContainer.setOrientation(LinearLayout.VERTICAL);
        cursorListContainer.setPadding(0, dp(4), 0, 0);
        cursorListContainer.addView(sectionDescription("Enable styles for cycling and choose the default cursor."));
        for (CursorStyle style : CursorStyle.values()) cursorListContainer.addView(cursorRow(style));
        card.addView(cursorListContainer);
        return card;
    }

    private SeekBar buildCursorSizeSeek() {
        SeekBar seek = new SeekBar(this);
        seek.setMax(ru.vasys.keycursor.core.Settings.MAX_CURSOR_SIZE_DP - ru.vasys.keycursor.core.Settings.MIN_CURSOR_SIZE_DP);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int value = ru.vasys.keycursor.core.Settings.MIN_CURSOR_SIZE_DP + progress;
                pendingCursorSizeDp = value;
                refreshCursorSizeLabel(value);
                refreshCursorPreviews(value);
                previewCursorSize(value);
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
        return seek;
    }

    private Spinner buildCursorColorSpinner() {
        Spinner spinner = new Spinner(this);
        String[] labels = new String[CursorColor.values().length];
        for (int i = 0; i < CursorColor.values().length; i++) labels[i] = CursorColor.values()[i].label;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        CursorColor initialColor = settings.cursorColor();
        int initialPosition = 0;
        for (int i = 0; i < CursorColor.values().length; i++) {
            if (CursorColor.values()[i] == initialColor) initialPosition = i;
        }
        spinner.setSelection(initialPosition, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
        return spinner;
    }

    private View buildDirectionCard() {
        LinearLayout card = card(
                "Direction",
                "Rotate or invert controls, and optionally blend held directions into intermediate angles.");

        rotateLandscapeCheck = optionSwitch("Rotate controls in landscape");
        rotateLandscapeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.rotateLandscapeControls() == isChecked) return;
            settings.setRotateLandscapeControls(isChecked);
            notifyService();
        });
        card.addView(rotateLandscapeCheck);

        blendDirectionsCheck = optionSwitch("Blend simultaneous directions");
        blendDirectionsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.blendDirections() == isChecked) return;
            settings.setBlendDirections(isChecked);
            notifyService();
        });
        card.addView(blendDirectionsCheck);

        card.addView(subheading("Portrait"));
        invertHorizontalPortraitCheck = optionSwitch("Invert left / right");
        invertHorizontalPortraitCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.invertHorizontalPortrait() == isChecked) return;
            settings.setInvertHorizontalPortrait(isChecked);
            notifyService();
        });
        card.addView(invertHorizontalPortraitCheck);

        invertVerticalPortraitCheck = optionSwitch("Invert up / down");
        invertVerticalPortraitCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.invertVerticalPortrait() == isChecked) return;
            settings.setInvertVerticalPortrait(isChecked);
            notifyService();
        });
        card.addView(invertVerticalPortraitCheck);

        card.addView(subheading("Landscape"));
        invertHorizontalLandscapeCheck = optionSwitch("Invert left / right");
        invertHorizontalLandscapeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.invertHorizontalLandscape() == isChecked) return;
            settings.setInvertHorizontalLandscape(isChecked);
            notifyService();
        });
        card.addView(invertHorizontalLandscapeCheck);

        invertVerticalLandscapeCheck = optionSwitch("Invert up / down");
        invertVerticalLandscapeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.invertVerticalLandscape() == isChecked) return;
            settings.setInvertVerticalLandscape(isChecked);
            notifyService();
        });
        card.addView(invertVerticalLandscapeCheck);
        return card;
    }

    private View buildBindingsCard() {
        LinearLayout card = card(
                "Bindings",
                "Hold Mouse key to show or hide the cursor. Single press toggles Normal / Hold; double press selects Scroll.");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(true);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-2, -2);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridParams.bottomMargin = dp(6);
        card.addView(grid, gridParams);

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
        primary.setGravity(Gravity.CENTER_VERTICAL);

        triggerButton = bindingButton("Mouse key", MouseAction.TRIGGER);
        LinearLayout.LayoutParams triggerParams = weighted();
        triggerParams.rightMargin = dp(4);
        primary.addView(triggerButton, triggerParams);

        cursorSwitchButton = bindingButton("Cursor switch", MouseAction.CURSOR_NEXT);
        LinearLayout.LayoutParams cursorSwitchParams = weighted();
        cursorSwitchParams.leftMargin = dp(4);
        primary.addView(cursorSwitchButton, cursorSwitchParams);
        card.addView(primary);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.setPadding(0, dp(8), 0, 0);
        legend.addView(legendItem("unbound", bindingColor(BindingState.NONE)));
        legend.addView(legendItem("default", bindingColor(BindingState.DEFAULT)));
        legend.addView(legendItem("custom", bindingColor(BindingState.CUSTOM)));
        card.addView(legend);

        TextView footer = sectionDescription(
                "Hidden: keys pass through. Visible: movement and click are captured. Tap to bind · hold to clear · focus to show key ID.");
        footer.setPadding(0, dp(8), 0, 0);
        card.addView(footer);
        return card;
    }

    private View buildExternalControlCard() {
        LinearLayout card = card(
                "External control",
                "Optional broadcast control for Key Mapper, Tasker or automation tools.");
        externalControlCheck = optionSwitch("Allow external control");
        externalControlCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settings.externalControlEnabled() == isChecked) return;
            settings.setExternalControlEnabled(isChecked);
            ExternalTriggerReceiver.setComponentEnabled(this, isChecked);
        });
        card.addView(externalControlCheck);
        return card;
    }

    private void addGridButton(GridLayout grid, MouseAction action, int iconRes) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(iconRes);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(16), dp(16), dp(16), dp(16));
        b.setContentDescription(label(action));
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setBackground(controlBackground());
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

        CursorGlyphView preview = new CursorGlyphView(this);
        preview.setStyle(style);
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
        refreshDirectionBehavior();
    }

    private void refreshDirectionBehavior() {
        if (rotateLandscapeCheck != null) rotateLandscapeCheck.setChecked(settings.rotateLandscapeControls());
        if (invertHorizontalPortraitCheck != null) invertHorizontalPortraitCheck.setChecked(settings.invertHorizontalPortrait());
        if (invertVerticalPortraitCheck != null) invertVerticalPortraitCheck.setChecked(settings.invertVerticalPortrait());
        if (invertHorizontalLandscapeCheck != null) invertHorizontalLandscapeCheck.setChecked(settings.invertHorizontalLandscape());
        if (invertVerticalLandscapeCheck != null) invertVerticalLandscapeCheck.setChecked(settings.invertVerticalLandscape());
        if (blendDirectionsCheck != null) blendDirectionsCheck.setChecked(settings.blendDirections());
    }

    private void refreshBindings() {
        for (Map.Entry<MouseAction, ImageButton> entry : bindingButtons.entrySet()) {
            entry.getValue().setColorFilter(bindingColor(settings.bindingState(entry.getKey())));
        }
        if (triggerButton != null) {
            BindingState state = settings.bindingState(MouseAction.TRIGGER);
            triggerButton.setText("Mouse key" + (state == BindingState.NONE ? "" : "  •"));
            triggerButton.setTextColor(bindingColor(state));
        }
        if (cursorSwitchButton != null) {
            BindingState state = settings.bindingState(MouseAction.CURSOR_NEXT);
            cursorSwitchButton.setText("Cursor switch" + (state == BindingState.NONE ? "" : "  •"));
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
        if (speedButton != null) speedButton.setText(String.valueOf(settings.speedPxPerTick()));
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
            int progress = value - ru.vasys.keycursor.core.Settings.MIN_CURSOR_SIZE_DP;
            cursorSizeSeek.setProgress(progress);
        }
    }

    private void refreshCursorSizeLabel(int value) {
        if (cursorSizeLabel != null) cursorSizeLabel.setText("Cursor size · " + value + " dp");
    }

    private void refreshCursorPreviews() {
        refreshCursorPreviews(settings.cursorSizeDp());
    }

    private void refreshCursorPreviews(int sizeDp) {
        int size = dp(sizeDp);
        CursorColor color = settings.cursorColor();
        if (cursorAppearancePreview != null) {
            cursorAppearancePreview.setStyle(settings.cursorStyle());
            cursorAppearancePreview.setCursorColor(color);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cursorAppearancePreview.getLayoutParams();
            lp.width = size;
            lp.height = size;
            cursorAppearancePreview.setLayoutParams(lp);
        }
        for (Map.Entry<CursorStyle, CursorGlyphView> entry : cursorPreviews.entrySet()) {
            CursorGlyphView preview = entry.getValue();
            preview.setCursorColor(color);
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

    private LinearLayout card(String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBackground(surfaceColor(), 18));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        TextView heading = text(title, 18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(2));
        card.addView(heading);

        if (description != null && !description.isEmpty()) {
            TextView body = sectionDescription(description);
            body.setPadding(0, 0, 0, dp(10));
            card.addView(body);
        }
        return card;
    }

    private TextView subheading(String value) {
        TextView t = text(value, 13);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private TextView settingLabel(String value) {
        TextView t = text(value, 14);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private View settingRow(String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView name = text(label, 14);
        name.setTypeface(null, Typeface.BOLD);
        row.addView(name, weighted());

        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(dp(88), -2);
        row.addView(control, controlParams);
        return row;
    }

    private TextView sectionDescription(String value) {
        TextView t = text(value, 12);
        t.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
        t.setLineSpacing(0f, 1.08f);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private Switch optionSwitch(String label) {
        Switch option = new Switch(this);
        option.setText(label);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setMinHeight(dp(44));
        option.setPadding(0, dp(1), 0, dp(1));
        return option;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setBackground(controlBackground());
        return b;
    }

    private Drawable controlBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, roundedBackground(controlActiveColor(), 12));
        states.addState(new int[]{android.R.attr.state_focused}, roundedBackground(controlActiveColor(), 12));
        states.addState(new int[]{}, roundedBackground(controlColor(), 12));
        return states;
    }

    private Drawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int pageBackgroundColor() {
        return isNightMode() ? 0xff101114 : 0xfff2f3f5;
    }

    private int surfaceColor() {
        return isNightMode() ? 0xff1b1d21 : 0xffffffff;
    }

    private int controlColor() {
        return isNightMode() ? 0xff292c31 : 0xffeceef1;
    }

    private int controlActiveColor() {
        return isNightMode() ? 0xff374151 : 0xffdbeafe;
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
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
