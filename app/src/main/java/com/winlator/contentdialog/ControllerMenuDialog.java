package com.winlator.contentdialog;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import app.gamenative.R;
import com.winlator.inputcontrols.ControllerManager;

public class ControllerMenuDialog extends ContentDialog {
    public ControllerMenuDialog(@NonNull Context context, NavigationDialog.NavigationListener listener) {
        super(context, R.layout.navigation_dialog);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(R.drawable.navigation_dialog_background);
        }
        // Hide the title bar and bottom bar
        findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        GridLayout grid = findViewById(R.id.main_menu_grid);
        grid.setColumnCount(3);

        // Check if physical controller is connected
        ControllerManager controllerManager = ControllerManager.getInstance();
        controllerManager.scanForDevices();
        boolean hasPhysicalController = !controllerManager.getDetectedDevices().isEmpty();

        addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.controller_manager, NavigationDialog.ACTION_CONTROLLER_MANAGER, listener);

        if (hasPhysicalController) {
            addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.edit_physical_controller, NavigationDialog.ACTION_EDIT_PHYSICAL_CONTROLLER, listener);
        }

        addMenuItem(context, grid, R.drawable.icon_motion_controls, R.string.motion_controls, NavigationDialog.ACTION_MOTION_CONTROLS, listener);
    }

    private void addMenuItem(Context context, GridLayout grid, int iconRes, int titleRes, int itemId, NavigationDialog.NavigationListener listener) {
        int padding = dpToPx(5, context);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnClickListener(view -> {
            listener.onNavigationItemSelected(itemId);
            dismiss();
        });

        int size = dpToPx(40, context);
        View icon = new View(context);
        icon.setBackground(AppCompatResources.getDrawable(context, iconRes));
        if (icon.getBackground() != null) {
            icon.getBackground().setTint(context.getColor(R.color.navigation_dialog_item_color));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(lp);
        layout.addView(icon);

        int width = dpToPx(96, context);
        TextView text = new TextView(context);
        text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(context.getString(titleRes));
        text.setGravity(Gravity.CENTER);
        text.setLines(2);
        text.setTextColor(context.getColor(R.color.navigation_dialog_item_color));
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
}
