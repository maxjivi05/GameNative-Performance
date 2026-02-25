package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.gamenative.R;

import com.winlator.renderer.GLRenderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.Locale;

public class PerformanceHUD extends FrameLayout {
    private static final String PREFS_NAME = "performance_hud_prefs";
    private static final String PREF_X = "hud_x";
    private static final String PREF_Y = "hud_y";
    private static final String PREF_HAS_POSITION = "hud_has_position";

    private final TextView tvFPS, tvGPU, tvCPU, tvRAM, tvPower, tvBattery;
    private final TextView tvCPUTemp, tvGPUTemp, tvBatteryTemp;
    private final LinearLayout container;
    private float currentFPS = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isVertical = false;
    private final GestureDetector gestureDetector;
    private boolean isTracking = false;
    private boolean isDragging = false;
    private float dX, dY;
    private int touchSlop;
    private float startX, startY;
    private boolean restoredPosition = false;

    // For GLRenderer-based FPS tracking
    private long lastFPSUpdateTimeMs = 0;

    // For per-core CPU tracking
    private long[] lastCoreTotals;
    private long[] lastCoreIdles;
    private int numCpuCores = 0;

    private final Runnable fpsUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (lastFPSUpdateTimeMs == 0) {
                lastFPSUpdateTimeMs = now;
                GLRenderer.getAndResetFrameCount(); // discard initial count
                handler.postDelayed(this, 500);
                return;
            }

            long elapsed = now - lastFPSUpdateTimeMs;
            if (elapsed >= 400) { // update roughly every 500ms
                int frames = GLRenderer.getAndResetFrameCount();

                // Try DXVK log first for true Vulkan/game FPS
                float guestFPS = readGuestFPS();
                if (guestFPS > 0) {
                    currentFPS = guestFPS;
                } else if (elapsed > 0) {
                    // Use GLRenderer frame counter (actual X server render frames)
                    currentFPS = (frames * 1000f) / elapsed;
                }
                lastFPSUpdateTimeMs = now;
                tvFPS.setText(String.format(Locale.ENGLISH, "FPS: %.1f", currentFPS));
            }
            handler.postDelayed(this, 500);
        }
    };

    private float readGuestFPS() {
        // Try to read DXVK HUD FPS if it's being redirected to a file
        File logDir = new File(getContext().getFilesDir(), "imagefs/tmp/dxvk_fps");
        if (logDir.exists() && logDir.isDirectory()) {
            File[] files = logDir.listFiles((dir, name) -> name.endsWith("_fps.log"));
            if (files != null && files.length > 0) {
                File latestFile = files[0];
                for (File f : files) if (f.lastModified() > latestFile.lastModified()) latestFile = f;

                try (RandomAccessFile raf = new RandomAccessFile(latestFile, "r")) {
                    long length = raf.length();
                    if (length > 0) {
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

        // Fallback to a direct file
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

        numCpuCores = Runtime.getRuntime().availableProcessors();
        if (numCpuCores < 1) numCpuCores = 1;
        lastCoreTotals = new long[numCpuCores];
        lastCoreIdles = new long[numCpuCores];

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleLayout();
                return true;
            }
        });

        handler.post(fpsUpdateRunnable);
        handler.post(metricsUpdateRunnable);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!restoredPosition) {
            restoredPosition = true;
            restorePosition();
        }
    }

    private void savePosition() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putFloat(PREF_X, getX())
            .putFloat(PREF_Y, getY())
            .putBoolean(PREF_HAS_POSITION, true)
            .apply();
    }

    private void restorePosition() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_HAS_POSITION, false)) {
            float savedX = prefs.getFloat(PREF_X, 0f);
            float savedY = prefs.getFloat(PREF_Y, 0f);
            // Clamp to screen bounds
            View parent = (View) getParent();
            if (parent != null) {
                savedX = Math.max(0, Math.min(savedX, parent.getWidth() - getWidth()));
                savedY = Math.max(0, Math.min(savedY, parent.getHeight() - getHeight()));
            }
            setX(savedX);
            setY(savedY);
        }
    }

    /**
     * Handles touch events dispatched directly from the XServerScreen pointerInteropFilter.
     * Returns true if the event was consumed (touch is within HUD bounds or part of an active HUD gesture).
     */
    public boolean handleTouchEvent(MotionEvent event) {
        float touchX = event.getRawX();
        float touchY = event.getRawY();

        int[] location = new int[2];
        container.getLocationOnScreen(location);
        float left = location[0];
        float top = location[1];
        float right = left + container.getWidth();
        float bottom = top + container.getHeight();

        boolean inBounds = touchX >= left && touchX <= right && touchY >= top && touchY <= bottom;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!inBounds) return false;
                isTracking = true;
                isDragging = false;
                startX = event.getRawX();
                startY = event.getRawY();
                dX = getX() - event.getRawX();
                dY = getY() - event.getRawY();
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!isTracking) return false;
                gestureDetector.onTouchEvent(event);
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
                if (!isTracking) return false;
                gestureDetector.onTouchEvent(event);
                if (isDragging) {
                    savePosition();
                }
                isDragging = false;
                isTracking = false;
                return true;
        }
        return false;
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

        tvCPU.setText(String.format(Locale.ENGLISH, "CPU: %d%%", getMaxCoreCpuUsage()));
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

    /**
     * Reads /proc/stat per-core lines (cpu0, cpu1, ...) and returns the highest
     * single-core usage percentage since the last call.
     */
    private int getMaxCoreCpuUsage() {
        int maxUsage = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("cpu")) continue;
                // Skip the aggregate "cpu " line (has a space after "cpu")
                if (line.startsWith("cpu ")) continue;

                // Parse "cpuN user nice system idle iowait irq softirq steal ..."
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5) continue;

                // Extract core index from "cpuN"
                int coreIndex;
                try {
                    coreIndex = Integer.parseInt(parts[0].substring(3));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (coreIndex >= numCpuCores) continue;

                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

                long totalTime = user + nice + system + idle + iowait + irq + softirq + steal;
                long idleTime = idle + iowait;

                long prevTotal = lastCoreTotals[coreIndex];
                long prevIdle = lastCoreIdles[coreIndex];
                lastCoreTotals[coreIndex] = totalTime;
                lastCoreIdles[coreIndex] = idleTime;

                if (prevTotal == 0) continue; // first reading, skip

                long totalDiff = totalTime - prevTotal;
                long idleDiff = idleTime - prevIdle;

                if (totalDiff <= 0) continue;

                int usage = (int) ((totalDiff - idleDiff) * 100 / totalDiff);
                usage = Math.max(0, Math.min(100, usage));
                if (usage > maxUsage) maxUsage = usage;
            }
        } catch (Exception e) {}
        return maxUsage;
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
        handler.removeCallbacks(fpsUpdateRunnable);
        handler.removeCallbacks(metricsUpdateRunnable);
    }
}
