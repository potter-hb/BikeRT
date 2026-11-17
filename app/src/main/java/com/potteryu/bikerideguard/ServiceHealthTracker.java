package com.potteryu.bikerideguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ServiceHealthTracker {
    private static final String PREFS = "service_health";
    private static final String KEY_CONNECTED_AT = "connected_at";
    private static final String KEY_UNBOUND_AT = "unbound_at";
    private static final String KEY_DESTROYED_AT = "destroyed_at";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_ERROR_AT = "last_error_at";
    private static final String KEY_BOOT_COUNT = "boot_count";
    private static final String KEY_UPDATE_TIME = "update_time";

    private ServiceHealthTracker() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void onServiceConnected(Context context) {
        prefs(context).edit()
                .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
                .putInt(KEY_BOOT_COUNT, bootCount(context))
                .putLong(KEY_UPDATE_TIME, packageUpdateTime(context))
                .apply();
    }

    static void onServiceUnbound(Context context) {
        prefs(context).edit()
                .putLong(KEY_UNBOUND_AT, System.currentTimeMillis())
                .putInt(KEY_BOOT_COUNT, bootCount(context))
                .apply();
    }

    static void onServiceDestroyed(Context context) {
        prefs(context).edit()
                .putLong(KEY_DESTROYED_AT, System.currentTimeMillis())
                .putInt(KEY_BOOT_COUNT, bootCount(context))
                .apply();
    }

    static void recordError(Context context, Throwable throwable) {
        String message = throwable == null
                ? "未知异常"
                : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        prefs(context).edit()
                .putString(KEY_LAST_ERROR, message)
                .putLong(KEY_LAST_ERROR_AT, System.currentTimeMillis())
                .apply();
    }

    static boolean isAccessibilityEnabled(Context context) {
        ComponentName component = new ComponentName(context, RideAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (component.flattenToString().equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    static String diagnosis(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean enabled = isAccessibilityEnabled(context);
        long connectedAt = prefs.getLong(KEY_CONNECTED_AT, 0L);
        long unboundAt = prefs.getLong(KEY_UNBOUND_AT, 0L);
        long destroyedAt = prefs.getLong(KEY_DESTROYED_AT, 0L);
        long errorAt = prefs.getLong(KEY_LAST_ERROR_AT, 0L);
        String error = prefs.getString(KEY_LAST_ERROR, "");

        StringBuilder result = new StringBuilder();
        result.append(enabled ? "当前状态：无障碍服务已开启\n" : "当前状态：无障碍服务已关闭\n");
        if (connectedAt > 0L) {
            result.append("最近连接：").append(formatTime(connectedAt)).append('\n');
        }
        if (unboundAt > 0L) {
            result.append("最近被系统解绑：").append(formatTime(unboundAt)).append('\n');
        }
        if (destroyedAt > 0L) {
            result.append("最近服务销毁：").append(formatTime(destroyedAt)).append('\n');
        }
        if (!enabled) {
            int previousBoot = prefs.getInt(KEY_BOOT_COUNT, -1);
            int currentBoot = bootCount(context);
            long previousUpdate = prefs.getLong(KEY_UPDATE_TIME, 0L);
            long currentUpdate = packageUpdateTime(context);
            if (previousBoot >= 0 && currentBoot >= 0 && previousBoot != currentBoot) {
                result.append("可能原因：手机重启后，系统重新加载了无障碍配置。\n");
            } else if (currentUpdate > previousUpdate && previousUpdate > 0L) {
                result.append("可能原因：覆盖安装或 App 更新后，系统重置了无障碍授权。\n");
            } else {
                result.append("可能原因：服务被系统权限监控、后台清理或强制停止。\n");
            }
        }
        if (errorAt > 0L && !TextUtils.isEmpty(error)) {
            result.append("最近异常：").append(formatTime(errorAt))
                    .append(" · ").append(error).append('\n');
        }
        result.append("\n安卓不允许 App 自行重新开启无障碍，需要你在系统设置中手动确认。");
        return result.toString();
    }

    private static int bootCount(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.BOOT_COUNT,
                -1
        );
    }

    private static long packageUpdateTime(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0L;
        }
    }

    private static String formatTime(long epochMs) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date(epochMs));
    }
}
