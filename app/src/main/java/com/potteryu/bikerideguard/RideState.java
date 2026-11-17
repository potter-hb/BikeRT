package com.potteryu.bikerideguard;

import android.content.Context;
import android.content.SharedPreferences;

final class RideState {
    static final int IDLE = 0;
    static final int RIDING = 1;
    static final int ENDING = 2;
    static final int COMPLETE = 3;
    static final int NEEDS_USER = 4;

    private static final String PREFS = "ride_state";
    private static final String KEY_STATE = "state";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_DURATION_MS = "duration_ms";
    private static final String KEY_MESSAGE = "message";

    private RideState() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int getState(Context context) {
        return prefs(context).getInt(KEY_STATE, IDLE);
    }

    static void setState(Context context, int state) {
        prefs(context).edit().putInt(KEY_STATE, state).apply();
    }

    static long getStartedAt(Context context) {
        return prefs(context).getLong(KEY_STARTED_AT, 0L);
    }

    static void setStartedAt(Context context, long startedAt) {
        prefs(context).edit().putLong(KEY_STARTED_AT, startedAt).apply();
    }

    static long getDurationMs(Context context) {
        return prefs(context).getLong(KEY_DURATION_MS, RideTimerService.RIDE_LIMIT_MS);
    }

    static void setDurationMs(Context context, long durationMs) {
        prefs(context).edit().putLong(KEY_DURATION_MS, durationMs).apply();
    }

    static String getMessage(Context context) {
        return prefs(context).getString(KEY_MESSAGE, "");
    }

    static void setMessage(Context context, String message) {
        prefs(context).edit().putString(KEY_MESSAGE, message == null ? "" : message).apply();
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
