package com.potteryu.bikerideguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RideAccessibilityService extends AccessibilityService {
    private static final long STEP_DELAY_MS = 1200L;
    private static final long CLOSE_RETRY_DELAY_MS = 2500L;
    private static final long CLOSE_FAILURE_TIMEOUT_MS = 15_000L;
    private static final int MAX_CLOSE_ATTEMPTS = 3;
    private static final int MAX_SCAN_ATTEMPTS = 18;
    private static final int MAX_END_ATTEMPTS = 28;

    private static RideAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanPending;
    private boolean endPending;
    private boolean closeLockClicked;
    private boolean closeConfirmationClicked;
    private int scanAttempts;
    private int endAttempts;
    private int closeLockAttempts;
    private long closeLockStartedAt;
    private long closeLockClickedAt;

    private final Runnable scanStep = new Runnable() {
        @Override
        public void run() {
            if (!scanPending || scanAttempts++ >= MAX_SCAN_ATTEMPTS) {
                scanPending = false;
                return;
            }
            if (!isMeituanForeground()) {
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }

            if (clickFirst("跳过广告", "跳过")) {
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }
            if (hasAnyText("骑行中", "骑行订单", "关锁", "结束骑行")) {
                scanPending = false;
                return;
            }
            if (clickFirst("扫一扫", "扫码", "扫码骑行", "扫码用车")) {
                scanPending = false;
                return;
            }
            handler.postDelayed(this, STEP_DELAY_MS);
        }
    };

    private final Runnable endStep = new Runnable() {
        @Override
        public void run() {
            if (!endPending || endAttempts++ >= MAX_END_ATTEMPTS) {
                endPending = false;
                RideTimerService.needsUser(
                        RideAccessibilityService.this,
                        "没有找到骑行订单或关锁按钮，请手动确认。"
                );
                return;
            }
            if (!isMeituanForeground()) {
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }

            if (clickFirst("跳过广告", "跳过")) {
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }
            if (hasAnyText(
                    "已关锁",
                    "关锁成功",
                    "已还车",
                    "行程已结束",
                    "还车成功",
                    "本次骑行已结束"
            )) {
                endPending = false;
                RideTimerService.complete(
                        RideAccessibilityService.this,
                        "已确认还车，计时结束。"
                );
                return;
            }

            if (closeLockClicked) {
                if (!closeConfirmationClicked && hasAnyText("确认关锁", "确认还车")) {
                    closeConfirmationClicked = clickFirst("确认关锁", "确认还车");
                    handler.postDelayed(this, STEP_DELAY_MS);
                    return;
                }
                long sinceLastAttempt = SystemClock.elapsedRealtime() - closeLockClickedAt;
                if (closeLockAttempts < MAX_CLOSE_ATTEMPTS
                        && sinceLastAttempt >= CLOSE_RETRY_DELAY_MS
                        && hasAnyText("关锁", "结束骑行", "我要还车")
                        && clickFirst("关锁", "结束骑行", "我要还车")) {
                    closeLockAttempts++;
                    closeConfirmationClicked = false;
                    closeLockClickedAt = SystemClock.elapsedRealtime();
                    handler.postDelayed(this, STEP_DELAY_MS);
                    return;
                }
                if (SystemClock.elapsedRealtime() - closeLockStartedAt
                        > CLOSE_FAILURE_TIMEOUT_MS) {
                    endPending = false;
                    RideTimerService.needsUser(
                            RideAccessibilityService.this,
                            "已尝试关锁" + closeLockAttempts
                                    + "次，但没有检测到成功页面，请立即检查骑行订单。"
                    );
                    return;
                }
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }

            if (hasAnyText("关锁", "结束骑行", "我要还车")) {
                if (clickFirst("关锁", "结束骑行", "我要还车")) {
                    closeLockClicked = true;
                    closeConfirmationClicked = false;
                    closeLockAttempts = 1;
                    closeLockStartedAt = SystemClock.elapsedRealtime();
                    closeLockClickedAt = SystemClock.elapsedRealtime();
                    handler.postDelayed(this, STEP_DELAY_MS);
                    return;
                }
            }

            if (clickFirst("骑行订单", "骑行中", "查看订单")) {
                handler.postDelayed(this, STEP_DELAY_MS);
                return;
            }

            handler.postDelayed(this, STEP_DELAY_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        ServiceHealthTracker.onServiceConnected(this);
        NotificationHelper.cancelAccessibilityDisabled(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleAccessibilityEvent(event);
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
        }
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null
                || !RideLauncher.MEITUAN_PACKAGE.equals(packageName.toString())) {
            return;
        }
        if (scanPending) {
            handler.removeCallbacks(scanStep);
            handler.postDelayed(scanStep, STEP_DELAY_MS);
        }
        if (endPending) {
            handler.removeCallbacks(endStep);
            handler.postDelayed(endStep, STEP_DELAY_MS);
        }
    }

    @Override
    public void onInterrupt() {
        cancelPendingWork();
    }

    @Override
    public void onDestroy() {
        ServiceHealthTracker.onServiceDestroyed(this);
        if (instance == this) {
            instance = null;
        }
        cancelPendingWork();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ServiceHealthTracker.onServiceUnbound(this);
        return super.onUnbind(intent);
    }

    static void requestScan(Context context) {
        if (!ensureAccessibility(context, true)) {
            return;
        }
        startWorkflow(false);
    }

    static void requestEndRide(Context context) {
        if (!ensureAccessibility(context, true)) {
            return;
        }
        startWorkflow(true);
    }

    private static void startWorkflow(boolean endRide) {
        RideAccessibilityService service = instance;
        if (service == null) {
            return;
        }
        if (endRide) {
            service.endPending = true;
            service.scanPending = false;
            service.endAttempts = 0;
            service.closeLockClicked = false;
            service.closeConfirmationClicked = false;
            service.closeLockAttempts = 0;
            service.closeLockStartedAt = 0L;
            service.closeLockClickedAt = 0L;
            service.handler.removeCallbacks(service.scanStep);
            service.handler.postDelayed(service.endStep, STEP_DELAY_MS);
        } else {
            service.scanPending = true;
            service.endPending = false;
            service.scanAttempts = 0;
            service.handler.removeCallbacks(service.endStep);
            service.handler.postDelayed(service.scanStep, STEP_DELAY_MS);
        }
    }

    private static boolean ensureAccessibility(Context context, boolean reportFailure) {
        if (instance != null) {
            return true;
        }
        if (reportFailure) {
            RideTimerService.needsUser(
                    context,
                    "无障碍服务未开启，请先在 App 中完成授权。"
            );
        }
        return false;
    }

    static void cancelPendingWork() {
        RideAccessibilityService service = instance;
        if (service == null) {
            return;
        }
        service.scanPending = false;
        service.endPending = false;
        service.closeLockClicked = false;
        service.closeConfirmationClicked = false;
        service.closeLockAttempts = 0;
        service.closeLockStartedAt = 0L;
        service.closeLockClickedAt = 0L;
        service.handler.removeCallbacks(service.scanStep);
        service.handler.removeCallbacks(service.endStep);
    }

    private boolean clickFirst(String... labels) {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        }
        if (root == null) {
            return false;
        }
        try {
            for (String label : labels) {
                List<AccessibilityNodeInfo> nodes = new ArrayList<>();
                collectMatches(root, normalize(label), nodes);
                for (AccessibilityNodeInfo node : nodes) {
                    AccessibilityNodeInfo clickable = clickableNode(node);
                    if (clickable != null
                            && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clickable.recycle();
                        return true;
                    }
                    if (clickable != null) {
                        clickable.recycle();
                    }
                }
                for (AccessibilityNodeInfo node : nodes) {
                    node.recycle();
                }
            }
            return false;
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        } finally {
            root.recycle();
        }
    }

    private boolean hasAnyText(String... labels) {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        }
        if (root == null) {
            return false;
        }
        try {
            for (String label : labels) {
                List<AccessibilityNodeInfo> nodes = new ArrayList<>();
                collectMatches(root, normalize(label), nodes);
                boolean found = !nodes.isEmpty();
                for (AccessibilityNodeInfo node : nodes) {
                    node.recycle();
                }
                if (found) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        } finally {
            root.recycle();
        }
    }

    private boolean isMeituanForeground() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        }
        if (root == null) {
            return false;
        }
        try {
            CharSequence packageName = root.getPackageName();
            return packageName != null
                    && RideLauncher.MEITUAN_PACKAGE.equals(packageName.toString());
        } catch (RuntimeException error) {
            ServiceHealthTracker.recordError(this, error);
            return false;
        } finally {
            root.recycle();
        }
    }

    private static void collectMatches(
            AccessibilityNodeInfo node,
            String label,
            List<AccessibilityNodeInfo> matches
    ) {
        if (!node.isVisibleToUser()) {
            return;
        }
        String text = nodeText(node);
        if (text.contains(label)) {
            matches.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectMatches(child, label, matches);
                child.recycle();
            }
        }
    }

    private static String nodeText(AccessibilityNodeInfo node) {
        List<CharSequence> values = Arrays.asList(
                node.getText(),
                node.getContentDescription(),
                node.getHintText()
        );
        StringBuilder builder = new StringBuilder();
        for (CharSequence value : values) {
            if (value != null) {
                builder.append(value).append(' ');
            }
        }
        return normalize(builder.toString());
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.CHINA).replaceAll("\\s+", "");
    }

    private static AccessibilityNodeInfo clickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (current.isClickable()) {
                return AccessibilityNodeInfo.obtain(current);
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        if (current != null) {
            current.recycle();
        }
        return null;
    }
}
