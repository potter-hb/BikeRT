package com.potteryu.bikerideguard;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class ServiceHealthJobService extends JobService {
    private static final int JOB_ID = 9101;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, ServiceHealthJobService.class)
        )
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        NotificationHelper.ensureChannels(this);
        if (!ServiceHealthTracker.isAccessibilityEnabled(this)) {
            NotificationHelper.showAccessibilityDisabled(this);
        } else {
            NotificationHelper.cancelAccessibilityDisabled(this);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
