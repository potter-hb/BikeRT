package com.potteryu.bikerideguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 51;
    private static final int COLOR_BG = 0xFFF6F7F3;
    private static final int COLOR_INK = 0xFF172724;
    private static final int COLOR_MUTED = 0xFF66726E;
    private static final int COLOR_PRIMARY = 0xFF126E63;
    private static final int COLOR_WARN = 0xFFC78320;

    private final List<StatusRow> statusRows = new ArrayList<>();
    private boolean testRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        setContentView(buildContent());
        ServiceHealthJobService.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildTopBar(), matchWrap());

        addSectionTitle(root, "运行权限");
        addStatusRow(
                root,
                R.drawable.ic_shield,
                "无障碍自动操作",
                "读取美团当前页面并执行允许的点击",
                this::openAccessibilitySettings
        );
        addRow(
                root,
                R.drawable.ic_info,
                "无障碍诊断",
                "查看最近连接、关闭和异常记录",
                this::showAccessibilityDiagnosis
        );
        addStatusRow(
                root,
                R.drawable.ic_bell,
                "通知",
                "显示骑行倒计时和关锁结果",
                this::openNotificationSettings
        );
        addStatusRow(
                root,
                R.drawable.ic_clock,
                "精确计时",
                "尽量保证第 50 分钟准时触发",
                this::openExactAlarmSettings
        );
        addStatusRow(
                root,
                R.drawable.ic_battery,
                "后台运行",
                "建议关闭电池优化并允许自启动",
                this::openBatterySettings
        );

        addSectionTitle(root, "快捷与测试");
        addRow(
                root,
                R.drawable.ic_grid,
                "控制中心开关",
                "把“50分钟骑行”添加到快捷开关",
                this::showQuickSettings
        );
        addRow(
                root,
                R.drawable.ic_test,
                "20 秒流程测试",
                "仅未骑行时使用，会尝试进入还车流程",
                this::confirmTest
        );

        addSectionTitle(root, "关于");
        addRow(
                root,
                R.drawable.ic_info,
                "版本与隐私",
                "版本 0.2.0 · 本地运行 · 不申请网络权限",
                () -> Toast.makeText(
                        this,
                        "骑行守门员 0.2.0\ncom.potteryu.bikerideguard",
                        Toast.LENGTH_LONG
                ).show()
        );

        TextView footer = text(
                "自动操作遇到广告、验证码、支付或未知页面时会停止，不会使用固定坐标盲点。",
                12,
                0xFF8A9691,
                Typeface.NORMAL
        );
        footer.setPadding(dp(4), dp(22), dp(4), 0);
        root.addView(footer, matchWrap());
        return scrollView;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("返回");
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setBackground(rounded(0xFFE9EDEA, dp(8)));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("设置", 24, COLOR_INK, Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return bar;
    }

    private void addSectionTitle(LinearLayout root, String title) {
        TextView view = text(title, 13, COLOR_MUTED, Typeface.BOLD);
        view.setLetterSpacing(0.07f);
        view.setPadding(dp(4), dp(28), dp(4), dp(10));
        root.addView(view, matchWrap());
    }

    private void addStatusRow(
            LinearLayout root,
            int icon,
            String title,
            String subtitle,
            Runnable action
    ) {
        LinearLayout row = addRow(root, icon, title, subtitle, action);
        View labelsView = row.getChildAt(1);
        TextView subtitleView = labelsView instanceof LinearLayout
                ? findSubtitle((LinearLayout) labelsView)
                : null;
        statusRows.add(new StatusRow(title, subtitle, subtitleView));
    }

    private LinearLayout addRow(
            LinearLayout root,
            int icon,
            String title,
            String subtitle,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(12), dp(14));
        row.setBackground(rounded(0xFFFFFFFF, dp(8)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(icon);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView titleView = text(title, 16, COLOR_INK, Typeface.BOLD);
        TextView subtitleView = text(subtitle, 12, COLOR_MUTED, Typeface.NORMAL);
        subtitleView.setPadding(0, dp(4), 0, 0);
        labels.addView(titleView, matchWrap());
        labels.addView(subtitleView, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        root.addView(row, params);
        return row;
    }

    private TextView findSubtitle(LinearLayout labels) {
        if (labels.getChildCount() < 2) {
            return null;
        }
        View child = labels.getChildAt(1);
        return child instanceof TextView ? (TextView) child : null;
    }

    private void refreshStatuses() {
        for (StatusRow row : statusRows) {
            if ("无障碍自动操作".equals(row.title)) {
                boolean enabled = isAccessibilityEnabled();
                row.subtitle.setText(enabled ? "已开启，可以读取美团页面" : "未开启，需要手动授权");
                row.subtitle.setTextColor(enabled ? COLOR_PRIMARY : COLOR_WARN);
            } else if ("通知".equals(row.title)) {
                NotificationManager manager = getSystemService(NotificationManager.class);
                boolean enabled = manager != null && manager.areNotificationsEnabled();
                row.subtitle.setText(enabled ? "已开启" : "未开启，无法显示倒计时和提醒");
                row.subtitle.setTextColor(enabled ? COLOR_PRIMARY : COLOR_WARN);
            } else if ("精确计时".equals(row.title)) {
                AlarmManager manager = getSystemService(AlarmManager.class);
                boolean enabled = manager != null && manager.canScheduleExactAlarms();
                row.subtitle.setText(enabled ? "已开启" : "未开启，系统可能延迟触发");
                row.subtitle.setTextColor(enabled ? COLOR_PRIMARY : COLOR_WARN);
            }
        }
    }

    private boolean isAccessibilityEnabled() {
        return ServiceHealthTracker.isAccessibilityEnabled(this);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showAccessibilityDiagnosis() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("无障碍诊断")
                .setMessage(ServiceHealthTracker.diagnosis(this))
                .setNegativeButton("关闭", null)
                .setPositiveButton("去开启", (dialog, which) -> openAccessibilitySettings())
                .show();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "当前系统不需要额外设置", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openBatterySettings() {
        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    private void showQuickSettings() {
        try {
            startActivity(new Intent("android.settings.QUICK_SETTINGS"));
        } catch (Exception ignored) {
            Toast.makeText(
                    this,
                    "请下拉控制中心，点击编辑并添加“50分钟骑行”",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void confirmTest() {
        int state = RideState.getState(this);
        if (state == RideState.RIDING || state == RideState.ENDING) {
            Toast.makeText(this, "请先取消当前计时", Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("20 秒流程测试")
                .setMessage("仅限未骑行时使用。测试会打开美团，并在 20 秒后尝试进入还车流程。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始测试", (dialog, which) -> startTest())
                .show();
    }

    private void startTest() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍自动操作", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            testRequested = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
            return;
        }
        RideState.clear(this);
        RideTimerService.start(this, 20_000L);
        Toast.makeText(this, "测试已开始，20 秒后触发", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && testRequested) {
            testRequested = false;
            startTest();
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            testRequested = false;
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StatusRow {
        final String title;
        final String baseSubtitle;
        final TextView subtitle;

        StatusRow(String title, String baseSubtitle, TextView subtitle) {
            this.title = title;
            this.baseSubtitle = baseSubtitle;
            this.subtitle = subtitle;
        }
    }
}
