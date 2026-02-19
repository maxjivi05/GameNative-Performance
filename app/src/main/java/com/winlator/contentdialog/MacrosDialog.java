package com.winlator.contentdialog;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.gamenative.R;
import com.winlator.winhandler.WinHandler;

import java.util.ArrayList;
import java.util.List;

/** Simple list-based macros UI. */
public final class MacrosDialog {


    public static final String PREF_PREFIX = "macros_"; // WinHandler references this

    public static void show(Activity activity, int slotIndex, WinHandler winHandler) {
        final ContentDialog dialog = new ContentDialog(activity, R.layout.macros_dialog_list);
        dialog.setTitle(R.string.input_macros);
        dialog.setIcon(R.drawable.icon_gamepad);

        boolean dark = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("dark_mode", false);

        RecyclerView rv = dialog.findViewById(R.id.RVMacros);
        rv.setLayoutManager(new LinearLayoutManager(activity));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        List<Item> items = buildItems(slotIndex, prefs);
        Adapter adapter = new Adapter(items, prefs, dark);   // ← pass dark
        rv.setAdapter(adapter);

        // force the hint / switch text color:
        if (dark) {
            TextView hint = dialog.findViewById(R.id.TVHint);
            if (hint != null) hint.setTextColor(0xFFFFFFFF);
            View sw = dialog.findViewById(R.id.SWIncludeTriggers);
            if (sw instanceof TextView) ((TextView) sw).setTextColor(0xFFFFFFFF);
        }

        // Orientation & metrics
        final boolean isLand =
                activity.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        final android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        final int screenW = dm.widthPixels;

        // --- Views --------------------------------------------------------------
        // Local (inside macros layout) buttons
        Button btEnableAllLocal  = dialog.findViewById(R.id.BTEnableAllLocal);
        Button btDisableAllLocal = dialog.findViewById(R.id.BTDisableAllLocal);

        // Bottom‑bar copies (in ContentDialog)
        Button btEnableAllBar  = dialog.findViewById(R.id.BTEnableAll);
        Button btDisableAllBar = dialog.findViewById(R.id.BTDisableAll);
        Button btCancel        = dialog.findViewById(R.id.BTCancel);
        Button btOk            = dialog.findViewById(R.id.BTConfirm);

        // Recycler
        rv = dialog.findViewById(R.id.RVMacros);
        rv.setNestedScrollingEnabled(false);
        rv.setLayoutManager(new LinearLayoutManager(activity));

        // Data / adapter
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        items = buildItems(slotIndex, prefs);
        adapter = new Adapter(items, prefs, dark);
        rv.setAdapter(adapter);

        // Include-triggers switch
        CompoundButton cb = dialog.findViewById(R.id.SWIncludeTriggers);
        String includeKey = prefKey(slotIndex, "IncludeTriggers");
        cb.setChecked(prefs.getBoolean(includeKey, false));
        SharedPreferences finalPrefs = prefs;
        cb.setOnCheckedChangeListener((buttonView, isChecked) ->
                finalPrefs.edit().putBoolean(includeKey, isChecked).apply());

        // Enable/Disable handlers (shared)
        List<Item> finalItems = items;
        Adapter finalAdapter = adapter;
        View.OnClickListener enableAll = v -> {
            for (Item it : finalItems) { it.enabled = true; finalPrefs.edit().putBoolean(it.prefKey, true).apply(); }
            finalAdapter.notifyDataSetChanged();
        };
        View.OnClickListener disableAll = v -> {
            for (Item it : finalItems) { it.enabled = false; finalPrefs.edit().putBoolean(it.prefKey, false).apply(); }
            finalAdapter.notifyDataSetChanged();
        };

        // Wire local buttons
        if (btEnableAllLocal != null)  btEnableAllLocal.setOnClickListener(enableAll);
        if (btDisableAllLocal != null) btDisableAllLocal.setOnClickListener(disableAll);

        // Wire bottom‑bar buttons
        if (btEnableAllBar != null)  btEnableAllBar.setOnClickListener(enableAll);
        if (btDisableAllBar != null) btDisableAllBar.setOnClickListener(disableAll);

        dialog.setOnConfirmCallback(() -> {
            if (winHandler != null) {
                try { winHandler.reloadTurboFromPrefs(); } catch (Throwable ignored) {}
            }
        });



        // --- Show before sizing -----------------------------------------------
        dialog.show();

        // --- Window width cap ---------------------------------------------------
        int winCapDp  = isLand ? 680 : 560;
        int winCapPx  = Math.round(winCapDp * dm.density);
        int winTarget = Math.min((int) (screenW * 0.95f), winCapPx);

        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(winTarget, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }

        // ---- Center the window and the inflated content ----
        if (w != null) {
            w.setGravity(android.view.Gravity.CENTER);   // center the dialog window itself
        }

        // The container in content_dialog.xml that hosts macros_dialog_list
        android.view.View frame = dialog.findViewById(R.id.FrameLayout);
        if (frame != null && frame.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams flp =
                    (android.widget.LinearLayout.LayoutParams) frame.getLayoutParams();
            flp.width = ViewGroup.LayoutParams.MATCH_PARENT;                 // give room to center
            flp.gravity = android.view.Gravity.CENTER_HORIZONTAL;            // center inside parent
            frame.setLayoutParams(flp);
        }

        // The root of macros_dialog_list that was added into the FrameLayout
        android.view.View root = dialog.findViewById(R.id.MacrosRoot);
        if (root != null) {
            ViewGroup.LayoutParams lp = root.getLayoutParams();
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams rlp =
                        (android.widget.FrameLayout.LayoutParams) lp;
                rlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;             // use column width
                rlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;        // center the child
                root.setLayoutParams(rlp);
            } else if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams rlp =
                        (android.widget.LinearLayout.LayoutParams) lp;
                rlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                rlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                root.setLayoutParams(rlp);
            }
        }

        // --- Column width (where list lives) ------------------------------------
        int colCapDp  = isLand ? 560 : 300;        // tune: you said ~400–480 vs ~260–300
        int colCapPx  = Math.round(colCapDp * dm.density);
        int colTarget = Math.min((int) (screenW * 0.90f), colCapPx);

        View column = dialog.findViewById(R.id.MacrosColumn);
        if (column != null) {
            ViewGroup.LayoutParams lp = column.getLayoutParams();
            lp.width = colTarget;
            column.setLayoutParams(lp);
        }

        // Center inside the ContentDialog frame
        frame = dialog.findViewById(R.id.FrameLayout);
        if (frame instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout.LayoutParams flp =
                    (android.widget.LinearLayout.LayoutParams) frame.getLayoutParams();
            flp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            frame.setLayoutParams(flp);
        }
        root = dialog.findViewById(R.id.MacrosRoot);
        if (root instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout.LayoutParams rlp =
                    (android.widget.FrameLayout.LayoutParams) root.getLayoutParams();
            rlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            root.setLayoutParams(rlp);
        }

        // --- Hybrid visibility & text sizes ------------------------------------
        // Local buttons shown in portrait, hidden in landscape.
        if (btEnableAllLocal != null)  btEnableAllLocal .setVisibility(isLand ? View.GONE  : View.VISIBLE);
        if (btDisableAllLocal != null) btDisableAllLocal.setVisibility(isLand ? View.GONE  : View.VISIBLE);

        // Bottom‑bar buttons shown in landscape, hidden in portrait.
        if (btEnableAllBar  != null) btEnableAllBar .setVisibility(isLand ? View.VISIBLE : View.GONE);
        if (btDisableAllBar != null) btDisableAllBar.setVisibility(isLand ? View.VISIBLE : View.GONE);

        // Prevent squish: make Cancel/OK WRAP and remove weight.
        java.util.function.Consumer<Button> deWeight = b -> {
            if (b == null) return;
            ViewGroup.LayoutParams lp = b.getLayoutParams();
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams ll = (android.widget.LinearLayout.LayoutParams) lp;
                ll.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                ll.weight = 0f;
                b.setLayoutParams(ll);
            }
        };
        deWeight.accept(btCancel);
        deWeight.accept(btOk);
        deWeight.accept(btEnableAllBar);
        deWeight.accept(btDisableAllBar);

        // Text sizes: a touch smaller in portrait so bottom bar never clips.
        float barSp   = isLand ? 18f : 14f;
        float localSp = isLand ? 16f : 14f;

        if (btEnableAllBar  != null) btEnableAllBar .setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, barSp);
        if (btDisableAllBar != null) btDisableAllBar.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, barSp);
        if (btCancel        != null) btCancel       .setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, barSp);
        if (btOk            != null) btOk           .setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, barSp);

        if (btEnableAllLocal  != null) btEnableAllLocal .setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, localSp);
        if (btDisableAllLocal != null) btDisableAllLocal.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, localSp);

        // Right‑align bottom‑bar row
        View llBar = dialog.findViewById(R.id.LLBottomBar);
        if (llBar instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) ((ViewGroup) llBar).getChildAt(1); // second child is the row
            if (row instanceof android.widget.LinearLayout) {
                ((android.widget.LinearLayout) row)
                        .setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            }
        }
    }


    // Build list of logical buttons matching WinHandler mapping
    private static List<Item> buildItems(int slot, SharedPreferences prefs) {
        ArrayList<Item> list = new ArrayList<>();
        add(list, slot, prefs, "A",       R.string.button_a);
        add(list, slot, prefs, "B",       R.string.button_b);
        add(list, slot, prefs, "X",       R.string.button_x);
        add(list, slot, prefs, "Y",       R.string.button_y);
        add(list, slot, prefs, "LB",      R.string.button_lb);
        add(list, slot, prefs, "RB",      R.string.button_rb);
        add(list, slot, prefs, "Back",    R.string.button_back);
        add(list, slot, prefs, "Start",   R.string.button_start);
        add(list, slot, prefs, "L3",      R.string.button_l3);
        add(list, slot, prefs, "R3",      R.string.button_r3);
        add(list, slot, prefs, "DpadUp",   R.string.dpad_up);
        add(list, slot, prefs, "DpadDown", R.string.dpad_down);
        add(list, slot, prefs, "DpadLeft", R.string.dpad_left);
        add(list, slot, prefs, "DpadRight",R.string.dpad_right);
        return list;
    }

    private static void add(List<Item> list, int slot, SharedPreferences sp, String logical, int labelRes) {
        String key = prefKey(slot, logical);
        boolean enabled = sp.getBoolean(key, false);
        list.add(new Item(labelRes, key, enabled));
    }

    public static String prefKey(int slot, String logical) {
        return PREF_PREFIX + "p" + (slot + 1) + "_" + logical;
    }

    // ---------------------------------------------------------------------------------------------

    private static final class Item {
        final int labelRes;
        final String prefKey;
        boolean enabled;
        Item(int labelRes, String prefKey, boolean enabled) {
            this.labelRes = labelRes; this.prefKey = prefKey; this.enabled = enabled;
        }
    }

    private static final class VH extends RecyclerView.ViewHolder {
        final TextView label; final ToggleButton toggle;
        VH(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.TVLabel);
            toggle = itemView.findViewById(R.id.TBToggle);
        }
    }

    private static final class Adapter extends RecyclerView.Adapter<VH> {
        private final List<Item> data;
        private final SharedPreferences prefs;
        private final boolean dark;
        private final android.content.res.ColorStateList whiteState;

        Adapter(List<Item> data, SharedPreferences prefs, boolean dark) {
            this.data = data; this.prefs = prefs; this.dark = dark;
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{}};
            int white = 0xFFFFFFFF;
            this.whiteState = new android.content.res.ColorStateList(states, new int[]{white, white});
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_macro_toggle, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Item it = data.get(pos);
            h.label.setText(h.itemView.getContext().getString(it.labelRes));
            h.toggle.setChecked(it.enabled);

            if (dark) {
                h.label.setTextColor(0xFFFFFFFF);
                // ToggleButton often uses a ColorStateList; force both states to white:
                h.toggle.setTextColor(whiteState);
            }

            h.toggle.setOnCheckedChangeListener((CompoundButton b, boolean isChecked) -> {
                it.enabled = isChecked;
                prefs.edit().putBoolean(it.prefKey, isChecked).apply();
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }


    public static void show(android.content.Context context, int slotIndex) {
        show((android.app.Activity) context, slotIndex, null);
    }

    private static void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            }
        }
    }

}