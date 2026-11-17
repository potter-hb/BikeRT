package com.potteryu.bikerideguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RideAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, RideTimerService.class)
                .setAction(RideTimerService.ACTION_DUE);
        context.startForegroundService(service);
    }
}
