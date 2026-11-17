package com.potteryu.bikerideguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

final class RideLauncher {
    static final String MEITUAN_PACKAGE = "com.sankuai.meituan";

    private RideLauncher() {
    }

    static boolean launchMeituan(Context context, boolean resetTask) {
        PackageManager manager = context.getPackageManager();
        Intent launch = manager.getLaunchIntentForPackage(MEITUAN_PACKAGE);
        if (launch == null) {
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (resetTask) {
            launch.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        context.startActivity(launch);
        return true;
    }

    static boolean openKnownBikeUri(Context context) {
        // Kept as a fallback for a verified URI captured from the installed Meituan app.
        Intent launch = new Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com/"));
        launch.setPackage(MEITUAN_PACKAGE);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launch.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        context.startActivity(launch);
        return true;
    }
}
