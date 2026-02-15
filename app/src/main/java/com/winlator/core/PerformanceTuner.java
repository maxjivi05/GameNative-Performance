package com.winlator.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PerformanceTuner {
    private static final String TAG = "PerformanceTuner";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable rootPerfRunnable;
    private static Runnable nonRootPerfRunnable;
    private static boolean isRootPerfRunning = false;
    private static boolean isNonRootPerfRunning = false;
    private static String cachedMaxFreq = null;

    private static boolean isLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("extras");
            isLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load extras library: " + e.getMessage());
        }
    }

    private static native void setAdrenoPerformanceModeNative(boolean enabled);

    public static void setAdrenoPerformanceMode(boolean enabled) {
        if (isLibraryLoaded) {
            try {
                setAdrenoPerformanceModeNative(enabled);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Native method setAdrenoPerformanceModeNative not found");
            }
        }
    }

    public interface RootCheckCallback {
        void onResult(boolean hasRoot);
    }

    public static void checkRootAccessAsync(RootCheckCallback callback) {
        executor.execute(() -> {
            boolean hasRoot = checkRootAccess();
            handler.post(() -> callback.onResult(hasRoot));
        });
    }

    public static boolean checkRootAccess() {
        java.lang.Process p = null;
        try {
            // Use a timeout to prevent hanging if root app is unresponsive
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            if (p.waitFor(5, TimeUnit.SECONDS)) {
                return p.exitValue() == 0;
            } else {
                p.destroy();
                return false;
            }
        } catch (Exception e) {
            if (p != null) p.destroy();
            return false;
        }
    }

    public static void startRootPerformanceMode() {
        if (isRootPerfRunning) return;
        isRootPerfRunning = true;
        
        rootPerfRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRootPerfRunning) return;
                
                executor.execute(() -> {
                    if (shouldReapplyRoot()) {
                        applyRootPerformanceSettings();
                    }
                });
                
                handler.postDelayed(this, 500);
            }
        };
        
        executor.execute(() -> {
            detectMaxFrequency();
            handler.post(rootPerfRunnable);
        });
    }

    public static void stopRootPerformanceMode() {
        isRootPerfRunning = false;
        if (rootPerfRunnable != null) {
            handler.removeCallbacks(rootPerfRunnable);
            rootPerfRunnable = null;
        }
    }

    public static void startNonRootPerformanceMode() {
        if (isNonRootPerfRunning) return;
        isNonRootPerfRunning = true;
        
        nonRootPerfRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isNonRootPerfRunning) return;
                setAdrenoPerformanceMode(true);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(nonRootPerfRunnable);
    }

    public static void stopNonRootPerformanceMode() {
        isNonRootPerfRunning = false;
        if (nonRootPerfRunnable != null) {
            handler.removeCallbacks(nonRootPerfRunnable);
            nonRootPerfRunnable = null;
        }
        setAdrenoPerformanceMode(false);
    }

    private static boolean shouldReapplyRoot() {
        String current = readNode("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        if (current == null || cachedMaxFreq == null) return true;
        
        try {
            long curVal = Long.parseLong(current.trim());
            long maxVal = Long.parseLong(cachedMaxFreq.trim());
            return curVal < maxVal;
        } catch (Exception e) {
            return true;
        }
    }

    private static void detectMaxFrequency() {
        String[] nodes = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/kernel/gpu/gpu_max_clock"
        };
        
        for (String node : nodes) {
            String val = readNode(node);
            if (val != null && !val.isEmpty()) {
                cachedMaxFreq = val.trim();
                return;
            }
        }
        
        String avail = readNode("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies");
        if (avail != null && !avail.isEmpty()) {
            String[] freqs = avail.trim().split("\\s+");
            cachedMaxFreq = freqs[freqs.length - 1];
        }
    }

    private static String readNode(String path) {
        java.lang.Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + path});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (p.waitFor(2, TimeUnit.SECONDS)) {
                return line;
            } else {
                p.destroy();
                return null;
            }
        } catch (Exception e) {
            if (p != null) p.destroy();
            return null;
        }
    }

    private static void applyRootPerformanceSettings() {
        StringBuilder script = new StringBuilder();
        script.append("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > $cpu; done\n");
        
        String[] gpuGovNodes = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/kgsl-3d0/governor",
            "/sys/class/devfreq/gpufreq/governor",
            "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_governor"
        };
        for (String node : gpuGovNodes) script.append("echo performance > ").append(node).append(" 2>/dev/null\n");

        script.append("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null\n");
        script.append("echo 1 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null\n");
        script.append("echo 1 > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null\n");
        script.append("echo 1 > /sys/class/kgsl/kgsl-3d0/force_no_nap 2>/dev/null\n");
        
        script.append("echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null\n");
        script.append("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null\n");
        script.append("echo 0 > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null\n");

        if (cachedMaxFreq != null) {
            script.append("echo ").append(cachedMaxFreq).append(" > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq 2>/dev/null\n");
            script.append("echo ").append(cachedMaxFreq).append(" > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null\n");
            script.append("echo ").append(cachedMaxFreq).append(" > /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null\n");
            script.append("echo ").append(cachedMaxFreq).append(" > /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null\n");
        }

        java.lang.Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(script.toString());
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            process.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (process != null) process.destroy();
        }
    }
}
