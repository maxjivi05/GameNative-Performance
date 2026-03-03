package com.winlator.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.xenvironment.ImageFs;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.gamenative.R;

public class WineInfo implements Parcelable {
    public static final WineInfo MAIN_WINE_VERSION = new WineInfo("wine", "9.2", "x86_64");
    // Matches: type-version[-subversion]-arch
    // Examples: proton-10.0-4-arm64ec, proton-9.0-arm64ec, wine-9.2-x86_64
    private static final Pattern pattern = Pattern.compile("^(wine|proton|Proton)\\-([0-9\\.]+)(?:\\-([0-9\\.]+))?\\-(x86|x86_64|arm64ec)(?:\\-([0-9]+))?$");
    public final String version;
    public final String type;
    public String subversion;
    public final String path;
    private String arch;

    public WineInfo(String type, String version, String arch) {
        this.type = type;
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
    }

    public WineInfo(String type, String version, String subversion, String arch, String path) {
        this.type = type;
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
    }

    public WineInfo(String type, String version, String arch, String path) {
        this.type = type;
        this.version = version;
        this.arch = arch;
        this.path = path;
    }

    private WineInfo(Parcel in) {
        type = in.readString();
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return arch.equals("x86_64") || arch.equals("arm64ec");
    }

    public boolean isArm64EC() { return arch.equals("arm64ec"); }

    public boolean isMainWineVersion() {
        WineInfo other = WineInfo.MAIN_WINE_VERSION;

        boolean pathMatches =
                (path == null && other.path == null) ||
                        (path != null && path.equals(other.path));

        return type.equals(other.type)
                && version.equals(other.version)
                && arch.equals(other.arch)
                && pathMatches;
    }

    public String getExecutable(Context context, boolean wow64Mode) {
        if (this == MAIN_WINE_VERSION) {
            File wineBinDir = new File(ImageFs.find(context).getRootDir(), "/opt/wine/bin");
            File wineBinFile = new File(wineBinDir, "wine");
            File winePreloaderBinFile = new File(wineBinDir, "wine-preloader");
            FileUtils.copy(new File(wineBinDir, wow64Mode ? "wine-wow64" : "wine32"), wineBinFile);
            FileUtils.copy(new File(wineBinDir, wow64Mode ? "wine-preloader-wow64" : "wine32-preloader"), winePreloaderBinFile);
            FileUtils.chmod(wineBinFile, 0771);
            FileUtils.chmod(winePreloaderBinFile, 0771);
            return wow64Mode ? "wine" : "wine64";
        }
        else return (new File(path, "/bin/wine64")).isFile() ? "wine64" : "wine";
    }

    public String identifier() {
        if (type.equals("proton"))
            return "proton-" + fullVersion() + "-"+ arch;
        else
            return "wine-" + fullVersion() + "-" + arch;
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        if (type.equals("proton"))
            return "Proton "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
        else
            return "Wine "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, ContentsManager contentsManager, String identifier) {
        ImageFs imageFs = ImageFs.find(context);
        String path = "";

        Log.d("WineInfo", "Creating WineInfo from identifier: " + identifier);

        if (identifier.equals(MAIN_WINE_VERSION.identifier())) return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, null);

        // Look up installed content profile by the original identifier
        ContentProfile wineProfile = contentsManager.getProfileByEntryName(identifier);

        // If content manager found a profile, strip the trailing version code suffix
        // (e.g., "proton-10.0-4-arm64ec-0" -> "proton-10.0-4-arm64ec")
        // for regex matching below
        String regexInput = identifier;
        if (wineProfile != null && (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON)) {
            // The entry name format is "verName-verCode", so strip from the LAST dash
            int lastDash = identifier.lastIndexOf('-');
            if (lastDash > 0) {
                String possibleVerCode = identifier.substring(lastDash + 1);
                try {
                    Integer.parseInt(possibleVerCode);
                    // It's a numeric version code suffix, strip it
                    regexInput = identifier.substring(0, lastDash).toLowerCase();
                } catch (NumberFormatException e) {
                    // Not a version code, use identifier as-is
                    regexInput = identifier.toLowerCase();
                }
            }
        }

        Matcher matcher = pattern.matcher(regexInput);

        if (matcher.find()) {
            String matchedType = matcher.group(1);
            String matchedVersion = matcher.group(2);
            String matchedSubversion = matcher.group(3); // may be null
            String matchedArch = matcher.group(4);

            // Reconstruct the full identifier for path lookups
            String fullId = matchedType.toLowerCase() + "-" + matchedVersion
                    + (matchedSubversion != null ? "-" + matchedSubversion : "")
                    + "-" + matchedArch;

            Log.d("WineInfo", "Parsed: type=" + matchedType + ", version=" + matchedVersion
                    + ", subversion=" + matchedSubversion + ", arch=" + matchedArch
                    + ", fullId=" + fullId);

            // Check bundled wine versions
            String[] wineVersions = context.getResources().getStringArray(R.array.bionic_wine_entries);
            for (String wineVersion : wineVersions) {
                if (wineVersion.equals(fullId)) {
                    path = imageFs.getRootDir().getPath() + "/opt/" + fullId;
                    break;
                }
            }

            // Check content manager install path (takes priority over bundled)
            if (wineProfile != null && (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON)) {
                path = contentsManager.getInstallDir(context, wineProfile).getPath();
            }

            // Fallback: check if the directory exists on disk even without a profile
            // (handles manually placed or previously installed wine versions)
            if (path.isEmpty()) {
                File optDir = new File(imageFs.getRootDir(), "/opt/" + fullId);
                if (optDir.exists() && optDir.isDirectory()) {
                    File binCheck = new File(optDir, "bin");
                    if (binCheck.exists()) {
                        path = optDir.getPath();
                        Log.d("WineInfo", "Found wine installation on disk at: " + path);
                    }
                }
            }

            Log.d("WineInfo", "Resolved path: " + (path.isEmpty() ? "(empty)" : path));
            return new WineInfo(matchedType, matchedVersion, matchedSubversion, matchedArch, path);
        }
        else {
            Log.w("WineInfo", "Failed to parse identifier: " + regexInput + " (original: " + identifier + ")");
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, null);
        }
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null ||wineVersion.equals(MAIN_WINE_VERSION.identifier());
    }
}
