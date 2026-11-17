package com.potteryu.bikerideguard;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity {
    public static final String EXTRA_OPEN_ACCESSIBILITY = "open_accessibility";
    private static final int REQUEST_NOTIFICATIONS = 40;
    private static final int COLOR_BG = 0xFFF6F7F3;
    private static final int COLOR_INK = 0xFF172724;
    private static final int COLOR_MUTED = 0xFF66726E;
    private static final int COLOR_PRIMARY = 0xFF126E63;
    private static final int COLOR_WARN = 0xFFC78320;

    private TimerRingView timerRing;
    private TextView timerText;
    private TextView stateText;
    private TextView helperText;
    private TextView permissionText;
    private Button primaryButton;
    private Button stopButton;
    private boolean userRequestedStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannels(this);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        setContentView(buildContent());
        ServiceHealthJobService.schedule(this);
        if (getIntent().getBooleanExtra(EXTRA_OPEN_ACCESSIBILITY, false)) {
            getIntent().removeExtra(EXTRA_OPEN_ACCESSIBILITY);
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildTopBar(), matchWrap());

        TextView eyebrow = text("当前状态", 13, COLOR_MUTED, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        eyebrow.setPadding(0, dp(34), 0, dp(8));
        root.addView(eyebrow, matchWrap());

        stateText = text("准备开始", 28, COLOR_INK, Typeface.BOLD);
        root.addView(stateText, matchWrap());

        helperText = text("从美团首页直接打开扫一扫", 15, COLOR_MUTED, Typeface.NORMAL);
        helperText.setPadding(0, dp(6), 0, dp(20));
        root.addView(helperText, matchWrap());

        root.addView(buildTimer(), matchWrap());

        primaryButton = new Button(this);
        primaryButton.setText("开始骑行");
        primaryButton.setTextSize(17);
        primaryButton.setTextColor(0xFFFFFFFF);
        primaryButton.setAllCaps(false);
        primaryButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        primaryButton.setCompoundDrawablePadding(dp(10));
        primaryButton.setBackground(rounded(COLOR_PRIMARY, dp(8)));
        primaryButton.setOnClickListener(v -> startRide());
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        primaryParams.setMargins(0, dp(24), 0, dp(8));
        root.addView(primaryButton, primaryParams);

        stopButton = new Button(this);
        stopButton.setText("取消本次计时");
        stopButton.setTextSize(16);
        stopButton.setTextColor(COLOR_INK);
        stopButton.setAllCaps(false);
        stopButton.setGravity(Gravity.CENTER);
        stopButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0);
        stopButton.setCompoundDrawablePadding(dp(8));
        stopButton.setBackground(outline(0xFFD6DEDA, COLOR_BG, dp(8)));
        stopButton.setOnClickListener(v -> {
            RideTimerService.stop(this);
            refresh();
        });
        root.addView(stopButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        LinearLayout permissionRow = buildPermissionRow();
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permissionParams.setMargins(0, dp(28), 0, 0);
        root.addView(permissionRow, permissionParams);

        TextView privacy = text(
                "所有识别与计时均在本机完成，不申请网络权限。",
                12,
                0xFF8A9691,
                Typeface.NORMAL
        );
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(20), 0, 0);
        root.addView(privacy, matchWrap());

        return scrollView;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_bike);
        logo.setPadding(dp(10), dp(10), dp(10), dp(10));
        logo.setBackground(rounded(0xFFE6EFEB, dp(8)));
        bar.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, 0, 0);
        TextView title = text("骑行守门员", 20, COLOR_INK, Typeface.BOLD);
        TextView subtitle = text("50 分钟自动还车", 12, COLOR_MUTED, Typeface.NORMAL);
        titleBlock.addView(title, matchWrap());
        titleBlock.addView(subtitle, matchWrap());
        bar.addView(titleBlock, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setContentDescription("设置");
        settings.setPadding(dp(12), dp(12), dp(12), dp(12));
        settings.setBackground(rounded(0xFFE9EDEA, dp(8)));
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View buildTimer() {
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(270)
        ));

        timerRing = new TimerRingView(this);
        frame.addView(timerRing, new FrameLayout.LayoutParams(
                dp(238),
                dp(238),
                Gravity.CENTER
        ));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        timerText = text("50:00", 44, COLOR_INK, Typeface.BOLD);
        timerText.setIncludeFontPadding(false);
        TextView unit = text("剩余时间", 12, COLOR_MUTED, Typeface.NORMAL);
        unit.setPadding(0, dp(8), 0, 0);
        center.addView(timerText, matchWrap());
        center.addView(unit, matchWrap());
        frame.addView(center, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        return frame;
    }

    private LinearLayout buildPermissionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(rounded(0xFFFFFFFF, dp(8)));
        row.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_shield);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text("自动操作状态", 15, COLOR_INK, Typeface.BOLD);
        permissionText = text("正在检查", 12, COLOR_MUTED, Typeface.NORMAL);
        labels.addView(title, matchWrap());
        labels.addView(permissionText, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return row;
    }

    private void startRide() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先在设置中开启无障碍自动操作", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            userRequestedStart = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
            return;
        }
        RideState.clear(this);
        RideTimerService.start(this);
        refresh();
    }

    private void refresh() {
        int state = RideState.getState(this);
        long startedAt = RideState.getStartedAt(this);
        String message = RideState.getMessage(this);
        boolean active = state == RideState.RIDING || state == RideState.ENDING;
        stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
        primaryButton.setEnabled(!active);

        if (active) {
            long duration = RideState.getDurationMs(this);
            long remaining = Math.max(0L, startedAt + duration - System.currentTimeMillis());
            long minutes = remaining / 60000L;
            long seconds = (remaining / 1000L) % 60L;
            timerText.setText(String.format(Locale.CHINA, "%02d:%02d", minutes, seconds));
            stateText.setText(state == RideState.ENDING ? "正在关锁" : "骑行计时中");
            helperText.setText(message.isEmpty() ? "保持美团在后台，到时间自动处理" : message);
            timerRing.setAccentColor(COLOR_PRIMARY);
            timerRing.setProgress(duration == 0L ? 0f : (float) remaining / duration);
            primaryButton.setText("骑行进行中");
        } else if (state == RideState.COMPLETE) {
            timerText.setText("完成");
            stateText.setText("已还车");
            helperText.setText(message.isEmpty() ? "骑行已结束，可以再次出发" : message);
            timerRing.setAccentColor(COLOR_PRIMARY);
            timerRing.setProgress(1f);
            primaryButton.setText("再次开始骑行");
        } else if (state == RideState.NEEDS_USER) {
            timerText.setText("确认");
            stateText.setText("需要检查");
            helperText.setText(message.isEmpty() ? "请打开美团确认关锁状态" : message);
            timerRing.setAccentColor(COLOR_WARN);
            timerRing.setProgress(0.25f);
            primaryButton.setText("重新开始骑行");
        } else {
            timerText.setText("50:00");
            stateText.setText("准备开始");
            helperText.setText("从美团首页直接打开扫一扫");
            timerRing.setAccentColor(COLOR_PRIMARY);
            timerRing.setProgress(1f);
            primaryButton.setText("开始骑行");
        }

        boolean ready = isAccessibilityEnabled();
        permissionText.setText(ready ? "无障碍已开启" : "需要在设置中完成授权");
        permissionText.setTextColor(ready ? COLOR_PRIMARY : COLOR_WARN);
    }

    private boolean isAccessibilityEnabled() {
        return ServiceHealthTracker.isAccessibilityEnabled(this);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS || grantResults.length == 0) {
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && userRequestedStart) {
            userRequestedStart = false;
            startRide();
        } else {
            userRequestedStart = false;
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "没有通知权限，无法可靠提醒还车", Toast.LENGTH_LONG).show();
            }
        }
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable outline(int strokeColor, int fillColor, int radius) {
        GradientDrawable drawable = rounded(fillColor, radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
