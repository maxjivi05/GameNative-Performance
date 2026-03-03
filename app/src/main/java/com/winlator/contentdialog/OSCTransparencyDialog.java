package com.winlator.contentdialog;

import android.content.Context;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.gamenative.PrefManager;
import app.gamenative.R;
import com.winlator.core.Callback;

public class OSCTransparencyDialog extends ContentDialog {
    public OSCTransparencyDialog(@NonNull Context context, float currentOpacity, Callback<Float> onOpacityChanged) {
        super(context, R.layout.osc_transparency_dialog);
        setTitle(R.string.osc_transparency);
        setIcon(R.drawable.icon_settings);

        final TextView tvValue = findViewById(R.id.TVTransparencyValue);
        final SeekBar seekBar = findViewById(R.id.SBTransparency);

        int progress = (int) (currentOpacity * 100);
        seekBar.setProgress(progress);
        tvValue.setText(progress + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvValue.setText(progress + "%");
                if (onOpacityChanged != null) onOpacityChanged.call(progress / 100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        setOnConfirmCallback(() -> {
            float finalOpacity = seekBar.getProgress() / 100.0f;
            PrefManager.setControlsOpacity(finalOpacity);
        });
    }
}
