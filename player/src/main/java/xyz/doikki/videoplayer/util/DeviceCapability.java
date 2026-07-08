package xyz.doikki.videoplayer.util;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

public final class DeviceCapability {
    public static final int MEMORY_LOW = 0;
    public static final int MEMORY_MEDIUM = 1;
    public static final int MEMORY_HIGH = 2;

    private static DeviceCapability instance;

    private final boolean isTV;
    private final boolean hasHevcHwDecoder;
    private final boolean supportsTunneledPlayback;
    private final int memoryClass;

    private DeviceCapability(Context context) {
        Context appContext = context.getApplicationContext();
        isTV = detectTV(appContext);
        hasHevcHwDecoder = hasHardwareDecoder("video/hevc");
        supportsTunneledPlayback = detectTunneledPlayback("video/hevc");
        memoryClass = detectMemoryClass(appContext);
    }

    public static synchronized DeviceCapability get(Context context) {
        if (instance == null) {
            instance = new DeviceCapability(context);
        }
        return instance;
    }

    public boolean isTV() {
        return isTV;
    }

    public boolean hasHevcHwDecoder() {
        return hasHevcHwDecoder;
    }

    public boolean supportsTunneledPlayback() {
        return supportsTunneledPlayback;
    }

    public boolean shouldUseSurfaceView() {
        return isTV && supportsTunneledPlayback;
    }

    public int getMemoryClass() {
        return memoryClass;
    }

    private static boolean detectTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature("android.hardware.type.television");
    }

    private static int detectMemoryClass(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int appMemoryClass = activityManager == null ? 256 : activityManager.getMemoryClass();
        if (appMemoryClass <= 128) {
            return MEMORY_LOW;
        }
        if (appMemoryClass <= 256) {
            return MEMORY_MEDIUM;
        }
        return MEMORY_HIGH;
    }

    private static boolean hasHardwareDecoder(String mimeType) {
        for (MediaCodecInfo codecInfo : getCodecInfos()) {
            if (codecInfo.isEncoder() || !supportsType(codecInfo, mimeType)) {
                continue;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (codecInfo.isHardwareAccelerated()) {
                    return true;
                }
            } else if (looksLikeHardwareDecoder(codecInfo.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean detectTunneledPlayback(String mimeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        for (MediaCodecInfo codecInfo : getCodecInfos()) {
            if (codecInfo.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
                if (capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private static boolean supportsType(MediaCodecInfo codecInfo, String mimeType) {
        for (String type : codecInfo.getSupportedTypes()) {
            if (mimeType.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeHardwareDecoder(String name) {
        String lowerName = name.toLowerCase();
        return !lowerName.startsWith("omx.google.")
                && !lowerName.startsWith("c2.android.")
                && !lowerName.contains("sw")
                && !lowerName.contains("ffmpeg");
    }

    private static MediaCodecInfo[] getCodecInfos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        }
        int count = MediaCodecList.getCodecCount();
        MediaCodecInfo[] infos = new MediaCodecInfo[count];
        for (int i = 0; i < count; i++) {
            infos[i] = MediaCodecList.getCodecInfoAt(i);
        }
        return infos;
    }
}
