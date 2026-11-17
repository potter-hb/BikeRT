package com.potteryu.bikerideguard;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public final class RideTimerService extends Service {
    static final String ACTION_START = "com.potteryu.bikerideguard.START";
    static final String ACTION_STOP = "com.potteryu.bikerideguard.STOP";
    static final String ACTION_DUE = "com.potteryu.bikerideguard.DUE";
    static final String ACTION_COMPLETE = "com.potteryu.bikerideguard.COMPLETE";
    static final String ACTION_NEEDS_USER = "com.potteryu.bikerideguard.NEEDS_USER";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_DURATION_MS = "duration_ms";

    static final long RIDE_LIMIT_MS = 50L * 60L * 1000L;
    private static final int ALARM_REQUEST_CODE = 2001;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRide();
            return START_NOT_STICKY;
        }
        if (ACTION_COMPLETE.equals(action)) {
            completeRide(intent == null ? null : intent.getStringExtra(EXTRA_MESSAGE));
            return START_NOT_STICKY;
        }
        if (ACTION_NEEDS_USER.equals(action)) {
            needUser(intent == null ? null : intent.getStringExtra(EXTRA_MESSAGE));
            return START_NOT_STICKY;
        }
        if (ACTION_DUE.equals(action)) {
            startDueWorkflow();
            return START_STICKY;
        }
        if (ACTION_START.equals(action) || RideState.getState(this) == RideState.RIDING) {
            long durationMs = intent != null && intent.hasExtra(EXTRA_DURATION_MS)
                    ? intent.getLongExtra(EXTRA_DURATION_MS, RIDE_LIMIT_MS)
                    : RideState.getDurationMs(this);
            startRideIfNeeded(durationMs);
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void startRideIfNeeded(long durationMs) {
        durationMs = Math.max(20_000L, durationMs);
        long startedAt = RideState.getStartedAt(this);
        if (startedAt == 0L || RideState.getState(this) != RideState.RIDING) {
            startedAt = System.currentTimeMillis();
            RideState.setStartedAt(this, startedAt);
            RideState.setDurationMs(this, durationMs);
            RideState.setState(this, RideState.RIDING);
            RideState.setMessage(this, "骑行计时中");
        } else {
            durationMs = RideState.getDurationMs(this);
        }
        long endsAt = startedAt + durationMs;
        startForeground(
                NotificationHelper.TIMER_NOTIFICATION_ID,
                NotificationHelper.buildTimer(this, endsAt)
        );
        scheduleAlarm(endsAt);
        if (!RideLauncher.launchMeituan(this, true)) {
            needUser("没有找到美团 App，请先安装并登录美团。");
            return;
        }
        RideAccessibilityService.requestScan(this);
    }

    private void startDueWorkflow() {
        long startedAt = RideState.getStartedAt(this);
        long endsAt = startedAt == 0L
                ? System.currentTimeMillis()
                : startedAt + RideState.getDurationMs(this);
        startForeground(
                NotificationHelper.TIMER_NOTIFICATION_ID,
                NotificationHelper.buildTimer(this, endsAt)
        );
        RideState.setState(this, RideState.ENDING);
        RideState.setMessage(this, "正在打开美团骑行订单并尝试关锁");
        if (!RideLauncher.launchMeituan(this, false)) {
            needUser("无法打开美团，请手动进入骑行订单关锁。");
            return;
        }
        RideAccessibilityService.requestEndRide(this);
    }

    private void completeRide(String message) {
        cancelAlarm();
        RideState.setState(this, RideState.COMPLETE);
        RideState.setMessage(this, message == null ? "骑行已结束" : message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NotificationHelper.TIMER_NOTIFICATION_ID);
        }
        RideTileService.requestTileRefresh(this);
        stopSelf();
    }

    private void needUser(String message) {
        cancelAlarm();
        String safeMessage = message == null
                ? "自动关锁未完成，请打开美团骑行订单手动确认。"
                : message;
        RideState.setState(this, RideState.NEEDS_USER);
        RideState.setMessage(this, safeMessage);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(
                    NotificationHelper.ALERT_NOTIFICATION_ID,
                    NotificationHelper.buildAlert(this, "需要你确认关锁", safeMessage)
            );
        }
        triggerAlertVibration();
        stopForeground(STOP_FOREGROUND_REMOVE);
        RideTileService.requestTileRefresh(this);
        stopSelf();
    }

    private void stopRide() {
        cancelAlarm();
        RideState.clear(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NotificationHelper.TIMER_NOTIFICATION_ID);
        }
        RideAccessibilityService.cancelPendingWork();
        RideTileService.requestTileRefresh(this);
        stopSelf();
    }

    private void scheduleAlarm(long endsAt) {
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        Intent alarmIntent = new Intent(this, RideAlarmReceiver.class)
                .setAction(ACTION_DUE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long triggerAt = Math.max(System.currentTimeMillis() + 1000L, endsAt);
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );
        }
    }

    private void cancelAlarm() {
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        Intent alarmIntent = new Intent(this, RideAlarmReceiver.class)
                .setAction(ACTION_DUE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private void triggerAlertVibration() {
        VibratorManager manager = getSystemService(VibratorManager.class);
        Vibrator vibrator = manager == null ? null : manager.getDefaultVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createWaveform(
                new long[]{0L, 500L, 250L, 600L, 250L, 900L},
                -1
        ));
    }

    static void start(Context context) {
        start(context, RIDE_LIMIT_MS);
    }

    static void start(Context context, long durationMs) {
        Intent intent = new Intent(context, RideTimerService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_MS, durationMs);
        context.startForegroundService(intent);
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, RideTimerService.class)
                .setAction(ACTION_STOP);
        context.startService(intent);
    }

    static void complete(Context context, String message) {
        Intent intent = new Intent(context, RideTimerService.class)
                .setAction(ACTION_COMPLETE)
                .putExtra(EXTRA_MESSAGE, message);
        context.startService(intent);
    }

    static void needsUser(Context context, String message) {
        Intent intent = new Intent(context, RideTimerService.class)
                .setAction(ACTION_NEEDS_USER)
                .putExtra(EXTRA_MESSAGE, message);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
