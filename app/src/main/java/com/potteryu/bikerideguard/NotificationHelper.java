package com.potteryu.bikerideguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

final class NotificationHelper {
    static final String TIMER_CHANNEL = "ride_timer";
    static final String ALERT_CHANNEL = "ride_alert_v2";
    static final String SERVICE_CHANNEL = "service_health";
    static final int TIMER_NOTIFICATION_ID = 1001;
    static final int ALERT_NOTIFICATION_ID = 1002;
    static final int SERVICE_NOTIFICATION_ID = 1003;

    private NotificationHelper() {
    }

    static void ensureChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel timer = new NotificationChannel(
                TIMER_CHANNEL,
                "骑行计时",
                NotificationManager.IMPORTANCE_LOW
        );
        timer.setDescription("显示骑行剩余时间");
        manager.createNotificationChannel(timer);

        NotificationChannel alert = new NotificationChannel(
                ALERT_CHANNEL,
                "还车提醒",
                NotificationManager.IMPORTANCE_HIGH
        );
        alert.setDescription("低打扰模式失败时提醒你确认还车");
        alert.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0L, 500L, 250L, 600L, 250L, 900L});
        manager.createNotificationChannel(alert);

        NotificationChannel service = new NotificationChannel(
                SERVICE_CHANNEL,
                "无障碍服务状态",
                NotificationManager.IMPORTANCE_LOW
        );
        service.setDescription("无障碍被系统关闭时提醒你重新开启");
        manager.createNotificationChannel(service);
    }

    static Notification buildTimer(Context context, long endsAt) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(context, RideTimerService.class)
                .setAction(RideTimerService.ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                context,
                1,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(context, TIMER_CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("骑行计时中")
                .setContentText("将在免费时长结束前自动尝试关锁")
                .setWhen(endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(
                        null,
                        "提前结束",
                        stopIntent
                ).build())
                .build();
    }

    static Notification buildAlert(Context context, String title, String message) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                2,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(context, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();
    }

    static void showAccessibilityDisabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_ACCESSIBILITY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                3,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new Notification.Builder(context, SERVICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("无障碍自动操作已关闭")
                .setContentText("点击重新开启，否则无法自动扫码或关锁")
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();
        manager.notify(SERVICE_NOTIFICATION_ID, notification);
    }

    static void cancelAccessibilityDisabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(SERVICE_NOTIFICATION_ID);
        }
    }
}
