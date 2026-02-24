package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.gamenative.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.Locale;

public class PerformanceHUD extends FrameLayout {
    private final TextView tvFPS, tvGPU, tvCPU, tvRAM, tvPower, tvBattery;
    private final TextView tvCPUTemp, tvGPUTemp, tvBatteryTemp;
    private final LinearLayout container;
    private float currentFPS = 0;
    private int frameCount = 0;
    private long lastFPSUpdateTime = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isVertical = false;
    private final GestureDetector gestureDetector;
    private boolean isDragging = false;
    private float dX, dY;
    private int touchSlop;
    private float startX, startY;

    private long lastCpuTotal = 0;
    private long lastCpuIdle = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCount++;
            long currentTimeNanos = System.nanoTime();
            if (lastFPSUpdateTime == 0) lastFPSUpdateTime = currentTimeNanos;

            if (currentTimeNanos - lastFPSUpdateTime >= 500_000_000L) {
                float guestFPS = readGuestFPS();
                if (guestFPS > 0) {
                    currentFPS = guestFPS;
                } else {
                    currentFPS = (frameCount * 1_000_000_000f) / (currentTimeNanos - lastFPSUpdateTime);
                }
                frameCount = 0;
                lastFPSUpdateTime = currentTimeNanos;
                tvFPS.setText(String.format(Locale.ENGLISH, "FPS: %.1f", currentFPS));
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private float readGuestFPS() {
        // Try to read DXVK HUD FPS if it's being redirected to a file
        // DXVK_HUD_LOG_PATH=/tmp/dxvk_fps creates files like /tmp/dxvk_fps/executable_fps.log
        File logDir = new File(getContext().getFilesDir(), "imagefs/tmp/dxvk_fps");
        if (logDir.exists() && logDir.isDirectory()) {
            File[] files = logDir.listFiles((dir, name) -> name.endsWith("_fps.log"));
            if (files != null && files.length > 0) {
                // Get the most recently modified log file
                File latestFile = files[0];
                for (File f : files) if (f.lastModified() > latestFile.lastModified()) latestFile = f;

                try (RandomAccessFile raf = new RandomAccessFile(latestFile, "r")) {
                    long length = raf.length();
                    if (length > 0) {
                        // Read the last line which contains the latest FPS
                        long pos = length - 1;
                        StringBuilder sb = new StringBuilder();
                        while (pos >= 0) {
                            raf.seek(pos);
                            char c = (char) raf.readByte();
                            if (c == '\n' && sb.length() > 0) break;
                            if (c != '\n' && c != '\r') sb.append(c);
                            pos--;
                        }
                        String lastLine = sb.reverse().toString().trim();
                        // DXVK log format is usually: "frame_count, fps" or just "fps" depending on version
                        if (lastLine.contains(",")) {
                            String[] parts = lastLine.split(",");
                            return Float.parseFloat(parts[parts.length - 1].trim());
                        } else {
                            return Float.parseFloat(lastLine);
                        }
                    }
                } catch (Exception e) {}
            }
        }
        
        // Fallback to a direct file if some other hook is writing to it
        File file = new File(getContext().getFilesDir(), "imagefs/tmp/dxvk_fps");
        if (file.exists() && !file.isDirectory()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                if (line != null) return Float.parseFloat(line.trim());
            } catch (Exception e) {}
        }
        return -1;
    }

    private final Runnable metricsUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateMetrics();
            handler.postDelayed(this, 1000);
        }
    };

    public PerformanceHUD(@NonNull Context context) {
        this(context, null);
    }

    public PerformanceHUD(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        View view = LayoutInflater.from(context).inflate(R.layout.performance_hud, this, true);
        container = view.findViewById(R.id.LLPerformanceHUD);
        tvFPS = view.findViewById(R.id.TVFPS);
        tvGPU = view.findViewById(R.id.TVGPU);
        tvCPU = view.findViewById(R.id.TVCPU);
        tvRAM = view.findViewById(R.id.TVRAM);
        tvPower = view.findViewById(R.id.TVPower);
        tvBattery = view.findViewById(R.id.TVBattery);

        tvCPUTemp = createTempTextView(context);
        tvGPUTemp = createTempTextView(context);
        tvBatteryTemp = createTempTextView(context);

        insertAfter(tvCPU, tvCPUTemp);
        insertAfter(tvGPU, tvGPUTemp);
        insertAfter(tvBattery, tvBatteryTemp);

        container.setBackgroundResource(R.drawable.hud_background);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleLayout();
                return true;
            }
        });

        container.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = false;
                    startX = event.getRawX();
                    startY = event.getRawY();
                    dX = getX() - event.getRawX();
                    dY = getY() - event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float distanceX = Math.abs(event.getRawX() - startX);
                    float distanceY = Math.abs(event.getRawY() - startY);
                    if (isDragging || distanceX > touchSlop || distanceY > touchSlop) {
                        isDragging = true;
                        setX(event.getRawX() + dX);
                        setY(event.getRawY() + dY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean wasDragging = isDragging;
                    isDragging = false;
                    return wasDragging;
            }
            return false;
        });

        Choreographer.getInstance().postFrameCallback(frameCallback);
        handler.post(metricsUpdateRunnable);
    }

    private TextView createTempTextView(Context context) {
        TextView tv = new TextView(context);
        tv.setTextColor(0xFFBBBBBB);
        tv.setTextSize(8);
        tv.setPadding(0, 0, 4, 0);
        return tv;
    }

    private void insertAfter(View anchor, View newcomer) {
        int index = container.indexOfChild(anchor);
        container.addView(newcomer, index + 1);
    }

    private void toggleLayout() {
        isVertical = !isVertical;
        container.setOrientation(isVertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LayoutParams lp = (LayoutParams) container.getLayoutParams();
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        container.setLayoutParams(lp);
    }

    private void updateMetrics() {
        Context context = getContext();
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            long microAmps = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int milliVolts = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
            int batteryTemp = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) : 0;

            double watts = Math.abs((double)microAmps * milliVolts) / 1_000_000_000.0;
            if (watts > 0 && watts < 0.01 && Math.abs(microAmps) > 0) watts *= 1000;
            
            tvBattery.setText(String.format(Locale.ENGLISH, "%d%%", batteryLevel));
            tvBatteryTemp.setText(String.format(Locale.ENGLISH, "(%.1f°C)", batteryTemp / 10.0));
            tvPower.setText(String.format(Locale.ENGLISH, "%.1fW", watts));
        }

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(mi);
            long usedMem = (mi.totalMem - mi.availMem) / (1024 * 1024);
            if (usedMem > 1024) {
                tvRAM.setText(String.format(Locale.ENGLISH, "RAM: %.1fGB", usedMem / 1024.0));
            } else {
                tvRAM.setText(String.format(Locale.ENGLISH, "RAM: %dMB", usedMem));
            }
        }

        tvCPU.setText(String.format(Locale.ENGLISH, "CPU: %d%%", getCpuUsage()));
        tvCPUTemp.setText(String.format(Locale.ENGLISH, "(%d°C)", getCpuTemp()));
        tvGPU.setText(String.format(Locale.ENGLISH, "GPU: %d%%", getGpuUsage()));
        tvGPUTemp.setText(String.format(Locale.ENGLISH, "(%d°C)", getGpuTemp()));
    }

    private int getCpuTemp() {
        String[] thermalZones = {"thermal_zone0", "thermal_zone1", "thermal_zone7", "thermal_zone10", "thermal_zone11"};
        for (String zone : thermalZones) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/thermal/" + zone + "/temp"))) {
                int temp = Integer.parseInt(reader.readLine().trim());
                if (temp > 1000) temp /= 1000;
                if (temp > 20 && temp < 100) return temp;
            } catch (Exception e) {}
        }
        return 0;
    }

    private int getGpuTemp() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/kgsl/kgsl-3d0/temp"))) {
            int temp = Integer.parseInt(reader.readLine().trim());
            if (temp > 1000) temp /= 1000;
            return temp;
        } catch (Exception e) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone11/temp"))) {
                int temp = Integer.parseInt(reader.readLine().trim());
                if (temp > 1000) temp /= 1000;
                return temp;
            } catch (Exception e2) {}
        }
        return 0;
    }

    private int getCpuUsage() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu")) {
                String[] parts = line.split("\\s+");
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = Long.parseLong(parts[5]);
                long irq = Long.parseLong(parts[6]);
                long softirq = Long.parseLong(parts[7]);

                long total = user + nice + system + idle + iowait + irq + softirq;
                long diffTotal = total - lastCpuTotal;
                long diffIdle = idle - lastCpuIdle;
                lastCpuTotal = total;
                lastCpuIdle = idle;
                if (diffTotal <= 0) return 0;
                return (int) Math.max(0, Math.min(100, (100 * (diffTotal - diffIdle) / diffTotal)));
            }
        } catch (Exception e) {}
        return 0;
    }

    private int getGpuUsage() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/kgsl/kgsl-3d0/gpubusy"))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    long busy = Long.parseLong(parts[0]);
                    long total = Long.parseLong(parts[1]);
                    if (total > 0) return (int) Math.max(0, Math.min(100, (100 * busy / total)));
                }
            }
        } catch (Exception e) {}
        return 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        handler.removeCallbacks(metricsUpdateRunnable);
    }
}
