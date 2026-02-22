package com.winlator.xenvironment.components;

import static com.winlator.core.ProcessHelper.splitCommand;

import android.content.Context;
import android.media.Image;
import android.os.Process;
import android.util.Log;

import com.winlator.PrefManager;
import com.winlator.box86_64.Box86_64Preset;
import com.winlator.box86_64.Box86_64PresetManager;
import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.core.Callback;
import com.winlator.core.DefaultVersion;
import com.winlator.core.FileUtils;
import com.winlator.core.GPUInformation;
import com.winlator.core.envvars.EnvVars;
import com.winlator.core.ProcessHelper;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.ImageFs;
import com.winlator.container.Container;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import com.winlator.core.PerformanceTuner;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import app.gamenative.PluviaApp;
import app.gamenative.events.AndroidEvent;
import app.gamenative.service.SteamService;

public class GlibcProgramLauncherComponent extends GuestProgramLauncherComponent {
    private String guestExecutable;
    private static int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private String box86Version = DefaultVersion.BOX86;
    private String box64Version = DefaultVersion.BOX64;
    private String box86Preset = Box86_64Preset.COMPATIBILITY;
    private String box64Preset = Box86_64Preset.COMPATIBILITY;
    private String steamType = DefaultVersion.STEAM_TYPE;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private boolean wow64Mode = true;
    private final ContentsManager contentsManager;
    private final ContentProfile wineProfile;
    private File workingDir;
    private Container container;
    private WineInfo wineInfo;

    public void setWineInfo(WineInfo wineInfo) {
        this.wineInfo = wineInfo;
    }
    public WineInfo getWineInfo() {
        return this.wineInfo;
    }

    public Container getContainer() { return this.container; }
    public void setContainer(Container container) { this.container = container; }

    public GlibcProgramLauncherComponent(ContentsManager contentsManager, ContentProfile wineProfile) {
        this.contentsManager = contentsManager;
        this.wineProfile = wineProfile;
    }

    private Runnable preUnpack;
    public void setPreUnpack(Runnable r) { this.preUnpack = r; }
    @Override
    public void start() {
        Log.d("GlibcProgramLauncherComponent", "Starting...");
        synchronized (lock) {
            stop();
            if (wineInfo != null && wineInfo.isArm64EC())
                extractEmulatorsDlls();
            else
                extractBox64Files();
            copyDefaultBox64RCFile();
            if (preUnpack != null) preUnpack.run();
            
            try {
                if (container != null) {
                    if (container.isRootPerformanceMode()) {
                        PerformanceTuner.startRootPerformanceMode();
                    } else {
                        PerformanceTuner.stopRootPerformanceMode();
                    }
                    
                    if (container.isForceAdrenoClocks()) {
                        PerformanceTuner.startNonRootPerformanceMode();
                        envVars.put("ADRENOTOOLS_GPU_TURBO", "1");
                    } else {
                        PerformanceTuner.stopNonRootPerformanceMode();
                    }
                }
            } catch (Throwable t) {
                Log.e("GlibcProgramLauncherComponent", "Failed to apply performance settings", t);
            }

            PluviaApp.events.emitJava(new AndroidEvent.SetBootingSplashText("Launching game..."));
            pid = execGuestProgram();
            Log.d("GlibcProgramLauncherComponent", "Process " + pid + " started");
            SteamService.setKeepAlive(true);
        }
    }

    @Override
    public void stop() {
        Log.d("GlibcProgramLauncherComponent", "Stopping...");
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                Log.d("GlibcProgramLauncherComponent", "Stopped process " + pid);
                pid = -1;
                List<ProcessHelper.ProcessInfo> subProcesses = ProcessHelper.listSubProcesses();
                for (ProcessHelper.ProcessInfo subProcess : subProcesses) {
                    Process.killProcess(subProcess.pid);
                }
                SteamService.setKeepAlive(false);
            }
            PerformanceTuner.stopRootPerformanceMode();
            execShellCommand("wineserver -k");
        }
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public boolean isWoW64Mode() {
        return wow64Mode;
    }

    public void setWoW64Mode(boolean wow64Mode) {
        this.wow64Mode = wow64Mode;
    }

    public String[] getBindingPaths() {
        return bindingPaths;
    }

    public void setBindingPaths(String[] bindingPaths) {
        this.bindingPaths = bindingPaths;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public String getBox86Version() { return box86Version; }

    public void setBox86Version(String box86Version) { this.box86Version = box86Version; }

    public String getBox64Version() { return box64Version; }

    public void setBox64Version(String box64Version) { this.box64Version = box64Version; }

    public String getBox86Preset() {
        return box86Preset;
    }

    public void setBox86Preset(String box86Preset) {
        this.box86Preset = box86Preset;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
    }

    private int execGuestProgram() {
        // Get the number of enabled players directly from ControllerManager.
        final int enabledPlayerCount = com.winlator.inputcontrols.ControllerManager.getInstance().getEnabledPlayerCount();
        for (int i = 0; i < 4; i++) {
            String memPath;
            if (i == 0) {
                // Player 1 uses the original, non-numbered path that is known to work.
                memPath = "/data/data/app.gamenative/files/imagefs/tmp/gamepad.mem";
            } else {
                // Players 2, 3, 4 use a 1-based index.
                memPath = "/data/data/app.gamenative/files/imagefs/tmp/gamepad" + i + ".mem";
            }

            File memFile = new File(memPath);
            memFile.getParentFile().mkdirs();
            try (RandomAccessFile raf = new RandomAccessFile(memFile, "rw")) {
                raf.setLength(64);
            } catch (IOException e) {
                Log.e("EVSHIM_HOST", "Failed to create mem file for player index "+i, e);
            }
        }

        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();

        PrefManager.init(context);
        boolean enableBox86_64Logs = PrefManager.getBoolean("enable_box86_64_logs", true);

        EnvVars envVars = new EnvVars();
        envVars.put("EVSHIM_MAX_PLAYERS", String.valueOf(enabledPlayerCount));
        addBox64EnvVars(envVars, enableBox86_64Logs);
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", imageFs.getRootDir().getPath() + "/tmp");
        envVars.put("DISPLAY", ":0");

        String winePath = wineProfile == null ? imageFs.getWinePath() + "/bin"
                : ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).getAbsolutePath();
        envVars.put("PATH", winePath + ":" +
                imageFs.getRootDir().getPath() + "/usr/bin:" +
                imageFs.getRootDir().getPath() + "/usr/local/bin");

        envVars.put("LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib");
        
        if (wineInfo != null && wineInfo.isArm64EC()) {
            envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/aarch64-linux-gnu");
            envVars.put("VK_LAYER_PATH", imageFs.getRootDir().getPath() + "/usr/share/vulkan/implicit_layer.d" + ":" + imageFs.getRootDir().getPath() + "/usr/share/vulkan/explicit_layer.d");
            envVars.put("XDG_DATA_DIRS", imageFs.getRootDir().getPath() + "/usr/share");
            envVars.put("EVSHIM_SHM_ID", "1");
            envVars.put("EVSHIM_SHM_NAME", "controller-shm0");
        } else {
            envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/x86_64-linux-gnu");
        }
        
        envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.getRootDir().getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
        envVars.put("FONTCONFIG_PATH", imageFs.getRootDir().getPath() + "/usr/etc/fonts");

        if ((new File(imageFs.getGlibc64Dir(), "libandroid-sysvshm.so")).exists() ||
                (new File(imageFs.getGlibc32Dir(), "libandroid-sysvshm.so")).exists
                        ()) {
            String ldPreload = "libredirect.so libandroid-sysvshm.so";
            String evshimPath = imageFs.getLibDir() + "/libevshim.so";
            if (new File(evshimPath).exists()) ldPreload += " " + evshimPath;
            envVars.put("LD_PRELOAD", ldPreload);
        }
        envVars.put("WINEESYNC_WINLATOR", "1");
        if (this.envVars != null) envVars.putAll(this.envVars);

        String box64Path = rootDir.getPath() + "/usr/local/bin/box64";

        // Check if box64 exists and log its details before executing
        File box64File = new File(box64Path);
        Log.d("GlibcProgramLauncherComponent", "About to execute box64 from: " + box64Path);

        String emulator = container != null ? container.getEmulator() : "box64";
        String command = getFinalCommand(winePath, emulator, envVars, box64Path, guestExecutable);
        Log.d("GlibcProgramLauncherComponent", "Final command: " + command);

        return ProcessHelper.exec(command, envVars.toStringArray(), workingDir != null ? workingDir : rootDir, (status) -> {
            Log.d("GlibcProgramLauncherComponent", "Process terminated " + pid + " with status " + status);
            synchronized (lock) {
                pid = -1;
            }
            SteamService.setKeepAlive(false);
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    private void extractEmulatorsDlls() {
        Context context = environment.getContext();
        File rootDir = environment.getImageFs().getRootDir();
        File system32dir = new File(rootDir + "/home/xuser/.wine/drive_c/windows/system32");
        boolean containerDataChanged = false;

        ImageFs imageFs = ImageFs.find(context);

        String wowbox64Version = container.getBox64Version();
        String fexcoreVersion = container.getFEXCoreVersion();

        Log.d("Extraction", "box64Version in use: " + wowbox64Version);
        Log.d("Extraction", "fexcoreVersion in use: " + fexcoreVersion);

        ContentProfile wowboxprofile = contentsManager.getProfileByEntryName("wowbox64-" + wowbox64Version);
        if (wowboxprofile != null) {
            contentsManager.applyContent(wowboxprofile);
        } else {
            Log.d("Extraction", "Extracting box64Version: " + wowbox64Version);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "wowbox64/wowbox64-" + wowbox64Version + ".tzst", system32dir);
        }
        container.putExtra("box64Version", wowbox64Version);
        containerDataChanged = true;

        ContentProfile fexprofile = contentsManager.getProfileByEntryName("fexcore-" + fexcoreVersion);
        if (fexprofile != null) {
            contentsManager.applyContent(fexprofile);
        } else {
            Log.d("Extraction", "Extracting fexcoreVersion: " + fexcoreVersion);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "fexcore/fexcore-" + fexcoreVersion + ".tzst", system32dir);
        }
        container.putExtra("fexcoreVersion", fexcoreVersion);

        containerDataChanged = true;
        if (containerDataChanged) container.saveData();
    }

    private String getFinalCommand(String winePath, String emulator, EnvVars envVars, String box64Path, String guestExecutable) {
        String command;
        if (wineInfo != null && wineInfo.isArm64EC()) {
            command = winePath + "/" + guestExecutable;
            if (emulator.toLowerCase().equals("fexcore"))
                envVars.put("HODLL", "libwow64fex.dll");
            else
                envVars.put("HODLL", "wowbox64.dll");
        }
        else
            command = box64Path + " " + guestExecutable;
        return command;
    }

    private void extractBox64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();
        PrefManager.init(context);
        String currentBox86Version = PrefManager.getString("current_box86_version", "");
        String currentBox64Version = PrefManager.getString("current_box64_version", "");
        File rootDir = imageFs.getRootDir();

        ContentProfile profile = contentsManager.getProfileByEntryName("box64-" + box64Version);
        if (profile != null) {
            contentsManager.applyContent(profile);
        }
        else {
            Log.d("Extraction", "exctracting box64 with box64Version " + box64Version);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context.getAssets(), "box86_64/box64-" + box64Version + ".tzst", rootDir);
        }
        PrefManager.putString("current_box64_version", box64Version);
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        String renderer = GPUInformation.getRenderer(context);
        if (renderer.contains("Mali"))
            envVars.put("BOX64_MMAP32", "0");
        envVars.put("BOX64_X11GLX", "1");
        File box64RCFile = new File(imageFs.getRootDir(), "/etc/config.box64rc");
        envVars.put("BOX64_RCFILE", box64RCFile.getPath());
    }

    public String execShellCommand(String command) {
        return execShellCommand(command, true);
    }

    public String execShellCommand(String command, boolean includeStderr) {
        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();

        PrefManager.init(context);
        StringBuilder output = new StringBuilder();
        EnvVars envVars = new EnvVars();
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", imageFs.getRootDir().getPath() + "/tmp");
        envVars.put("DISPLAY", ":0");

        String winePath = wineProfile == null ? imageFs.getWinePath() + "/bin"
                : ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).getAbsolutePath();
        envVars.put("PATH", winePath + ":" +
                imageFs.getRootDir().getPath() + "/usr/bin:" +
                imageFs.getRootDir().getPath() + "/usr/local/bin");

        envVars.put("LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib");
        
        if (wineInfo != null && wineInfo.isArm64EC()) {
            envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/aarch64-linux-gnu");
            envVars.put("VK_LAYER_PATH", imageFs.getRootDir().getPath() + "/usr/share/vulkan/implicit_layer.d" + ":" + imageFs.getRootDir().getPath() + "/usr/share/vulkan/explicit_layer.d");
            envVars.put("XDG_DATA_DIRS", imageFs.getRootDir().getPath() + "/usr/share");
        } else {
            envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/x86_64-linux-gnu");
        }
        
        envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.getRootDir().getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
        envVars.put("FONTCONFIG_PATH", imageFs.getRootDir().getPath() + "/usr/etc/fonts");

        if ((new File(imageFs.getGlibc64Dir(), "libandroid-sysvshm.so")).exists() ||
                (new File(imageFs.getGlibc32Dir(), "libandroid-sysvshm.so")).exists
                        ()) {
            String ldPreload = "libredirect.so libandroid-sysvshm.so";
            String evshimPath = imageFs.getLibDir() + "/libevshim.so";
            if (new File(evshimPath).exists()) ldPreload += " " + evshimPath;
            envVars.put("LD_PRELOAD", ldPreload);
        }
        envVars.put("WINEESYNC_WINLATOR", "1");
        if (this.envVars != null) envVars.putAll(this.envVars);

        String box64Path = rootDir.getPath() + "/usr/local/bin/box64";
        String emulator = container != null ? container.getEmulator() : "box64";
        String finalCommand = getFinalCommand(winePath, emulator, envVars, box64Path, command);

        // Execute the command and capture its output
        try {
            Log.d("GlibcProgramLauncherComponent", "Shell command is " + finalCommand);
            java.lang.Process process = Runtime.getRuntime().exec(finalCommand, envVars.toStringArray(), workingDir != null ? workingDir : imageFs.getRootDir());
            
            final StringBuilder stdout = new StringBuilder();
            final StringBuilder stderr = new StringBuilder();
            
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) stdout.append(line).append("\n");
                } catch (IOException e) {}
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) stderr.append(line).append("\n");
                } catch (IOException e) {}
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            process.waitFor();
            stdoutThread.join(5000);
            stderrThread.join(5000);
            
            output.append(stdout);
            if (includeStderr) output.append(stderr);
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        }

        // Format output: trim trailing whitespace/newlines
        return output.toString().trim();
    }
}
