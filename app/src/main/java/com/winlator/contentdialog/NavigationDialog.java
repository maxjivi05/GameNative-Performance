package com.winlator.contentdialog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.view.LayoutInflater;
import app.gamenative.PrefManager;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import app.gamenative.R;

public class NavigationDialog extends ContentDialog {

    public static final int ACTION_KEYBOARD = 1;
    public static final int ACTION_INPUT_CONTROLS = 2;
    public static final int ACTION_EXIT_GAME = 3;
    public static final int ACTION_EDIT_CONTROLS = 4;
    public static final int ACTION_EDIT_PHYSICAL_CONTROLLER = 5;
    public static final int ACTION_STRETCH_TO_FULLSCREEN = 6;
    public static final int ACTION_CONTROLLER_MANAGER = 7;
    public static final int ACTION_MOTION_CONTROLS = 8;
    public static final int ACTION_PAUSE_GAME = 9;
    public static final int ACTION_TASK_MANAGER = 10;
    public static final int ACTION_HUD = 11;
    public static final int ACTION_SHOW_JOYSTICKS = 12;
    public static final int ACTION_TOUCH_MENU = 13;
    public static final int ACTION_TOUCH_TRANSPARENCY = 14;
    public static final int ACTION_SCREEN_EFFECT = 15;
    public static final int ACTION_NATIVE_RENDERING = 16;
    public static final int ACTION_TOOLS_MENU = 17;
    public static final int ACTION_CONTROLLER_MENU = 18;
    private final boolean areControlsVisible;
    private final boolean areJoysticksVisible;

    public interface NavigationListener {
        void onNavigationItemSelected(int itemId);
    }

    public NavigationDialog(@NonNull Context context, boolean areControlsVisible, boolean isGamePaused, boolean areJoysticksVisible, NavigationListener listener) {
        super(context, R.layout.navigation_dialog);
        this.areControlsVisible = areControlsVisible;
        this.areJoysticksVisible = areJoysticksVisible;

        if (getWindow() != null) {
            getWindow().setGravity(Gravity.CENTER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        View rootView = getContentView();
        rootView.setBackgroundResource(R.drawable.content_dialog_background);
        int pad = dpToPx(16, context);
        rootView.setPadding(pad, pad, pad, pad);

        findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);
        findViewById(R.id.LLTopPanel).setVisibility(View.GONE);

        FrameLayout frameLayout = findViewById(R.id.FrameLayout);
        LinearLayout.LayoutParams flp = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
        flp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        flp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        flp.weight = 0;
        frameLayout.setLayoutParams(flp);

        GridLayout grid = findViewById(R.id.main_menu_grid);
        grid.setBackgroundColor(Color.TRANSPARENT);
        grid.setPadding(dpToPx(4, context), dpToPx(4, context), dpToPx(4, context), dpToPx(4, context));
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            grid.setColumnCount(4);
        } else {
            grid.setColumnCount(3);
        }

        int pauseIcon = isGamePaused ? R.drawable.icon_play : R.drawable.icon_pause;
        int pauseText = isGamePaused ? R.string.resume_game : R.string.pause_game;
        addMenuItem(context, grid, pauseIcon, pauseText, ACTION_PAUSE_GAME, listener, 1.0f);

        addMenuItem(context, grid, R.drawable.icon_input_controls, R.string.touch_controller, ACTION_TOUCH_MENU, listener, 1.0f);

        addMenuItem(context, grid, R.drawable.icon_monitor, R.string.container_config_tab_graphics, ACTION_STRETCH_TO_FULLSCREEN, listener, 1.0f);

        addMenuItem(context, grid, R.drawable.icon_settings, R.string.tools, ACTION_TOOLS_MENU, listener, 1.0f);

        addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.controller, ACTION_CONTROLLER_MENU, listener, 1.0f);

        addMenuItem(context, grid, R.drawable.icon_exit, R.string.exit_game, ACTION_EXIT_GAME, listener, 1.0f);
    }

    private void addMenuItem(Context context, GridLayout grid, int iconRes, int titleRes, int itemId, NavigationListener listener, float alpha) {
        int padding = dpToPx(12, context);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.TRANSPARENT);
        
        layout.setOnClickListener(view -> {
            if (itemId == ACTION_TOUCH_MENU) {
                showTouchMenu(context, areControlsVisible, areJoysticksVisible, listener);
            } else if (itemId == ACTION_TOOLS_MENU) {
                showToolsMenu(context, listener);
            } else if (itemId == ACTION_CONTROLLER_MENU) {
                showControllerMenu(context, listener);
            } else {
                listener.onNavigationItemSelected(itemId);
            }
            dismiss();
        });

        int size = dpToPx(32, context);
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        icon.setAlpha(alpha);
        icon.setBackgroundColor(Color.TRANSPARENT);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(lp);
        layout.addView(icon);

        int width = dpToPx(80, context);
        TextView text = new TextView(context);
        text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(context.getString(titleRes));
        text.setGravity(Gravity.CENTER);
        text.setLines(2);
        text.setTextSize(11);
        text.setTextColor(Color.WHITE);
        text.setShadowLayer(2.0f, 0, 0, Color.BLACK);
        text.setAlpha(alpha);
        Typeface tf = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_regular);
        if (tf != null) {
            text.setTypeface(tf);
        }
        layout.addView(text);

        grid.addView(layout);
    }


    public int dpToPx(float dp, Context context){
        return (int) (dp * context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static void showTouchMenu(Context context, boolean areControlsVisible, boolean areJoysticksVisible, NavigationListener listener) {
        NavigationDialog dialog = new NavigationDialog(context, areControlsVisible, false, areJoysticksVisible, listener);
        dialog.findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        dialog.findViewById(R.id.LLTopPanel).setVisibility(View.GONE);
        
        GridLayout grid = dialog.findViewById(R.id.main_menu_grid);
        grid.removeAllViews();
        grid.setPadding(dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context));
        grid.setColumnCount(2);
        
        int controlsTextRes = areControlsVisible ? R.string.hide_controls : R.string.show_controls;
        dialog.addMenuItem(context, grid, R.drawable.icon_input_controls, controlsTextRes, ACTION_INPUT_CONTROLS, listener, 1.0f);

        int joysticksTextRes = areJoysticksVisible ? R.string.hide_joysticks : R.string.show_joysticks;
        dialog.addMenuItem(context, grid, R.drawable.icon_gamepad, joysticksTextRes, ACTION_SHOW_JOYSTICKS, listener, 1.0f);

        dialog.addMenuItem(context, grid, R.drawable.icon_popup_menu_edit, R.string.edit_controls, ACTION_EDIT_CONTROLS, listener, 1.0f);
        dialog.addMenuItem(context, grid, R.drawable.icon_settings, R.string.touch_transparency, ACTION_TOUCH_TRANSPARENCY, listener, 1.0f);
        
        dialog.show();
    }

    public static void showToolsMenu(Context context, NavigationListener listener) {
        NavigationDialog dialog = new NavigationDialog(context, false, false, false, listener);
        dialog.findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        dialog.findViewById(R.id.LLTopPanel).setVisibility(View.GONE);
        
        GridLayout grid = dialog.findViewById(R.id.main_menu_grid);
        grid.removeAllViews();
        grid.setPadding(dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context));
        grid.setColumnCount(2);
        
        dialog.addMenuItem(context, grid, R.drawable.icon_task_manager, R.string.task_manager, ACTION_TASK_MANAGER, listener, 1.0f);
        dialog.addMenuItem(context, grid, R.drawable.icon_keyboard, R.string.keyboard, ACTION_KEYBOARD, listener, 1.0f);
        
        dialog.show();
    }

    public static void showControllerMenu(Context context, NavigationListener listener) {
        NavigationDialog dialog = new NavigationDialog(context, false, false, false, listener);
        dialog.findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        dialog.findViewById(R.id.LLTopPanel).setVisibility(View.GONE);
        
        GridLayout grid = dialog.findViewById(R.id.main_menu_grid);
        grid.removeAllViews();
        grid.setPadding(dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context), dialog.dpToPx(2, context));
        grid.setColumnCount(3);
        
        dialog.addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.controller_manager, ACTION_CONTROLLER_MANAGER, listener, 1.0f);
        dialog.addMenuItem(context, grid, R.drawable.icon_settings, R.string.edit_physical_controller, ACTION_EDIT_PHYSICAL_CONTROLLER, listener, 1.0f);
        dialog.addMenuItem(context, grid, R.drawable.icon_motion_controls, R.string.motion_controls, ACTION_MOTION_CONTROLS, listener, 1.0f);
        
        dialog.show();
    }

    public static void showTouchTransparencyDialog(Context context) {
        ContentDialog dialog = new ContentDialog(context, R.layout.touch_transparency_dialog);
        dialog.setTitle(R.string.touch_transparency);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        ((TextView)dialog.findViewById(R.id.BTConfirm)).setText(R.string.save);

        View frameLayout = dialog.findViewById(R.id.FrameLayout);
        if (frameLayout != null) {
            ViewGroup.LayoutParams lp = frameLayout.getLayoutParams();
            lp.width = com.winlator.core.AppUtils.getPreferredDialogWidth(context);
            frameLayout.setLayoutParams(lp);
        }

        SeekBar seekBar = dialog.findViewById(R.id.transparency_seekbar);
        TextView progressText = dialog.findViewById(R.id.transparency_text);

        int currentProgress = (int) (PrefManager.getTouchTransparency() * 100);
        seekBar.setProgress(currentProgress);
        progressText.setText(currentProgress + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressText.setText(progress + "%");
                float alpha = progress / 100.0f;
                PrefManager.setTouchTransparency(alpha);
                app.gamenative.PluviaApp.events.emitJava(new app.gamenative.events.AndroidEvent.TouchTransparencyChanged(alpha));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dialog.show();
    }
}
