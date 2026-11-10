package local.ridewatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public final class MainActivity extends Activity {
    private LinearLayout content;
    private TextView status, last, preview;
    private Spinner target, day;
    private EditText origin, destination, begin, end, fare, interval, marker, originLine, destinationLine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() { renderStatus(); handler.postDelayed(this, 1500); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); Notices.channels(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(28)); content.setBackgroundColor(Color.rgb(244,247,248));
        scroll.addView(content); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom); return insets;
        });
        title("顺风车订单提醒", 27); text("设置路线，留在订单列表，匹配后通知你。", 15);
        LinearLayout state = card();
        status = label(state, "正在读取状态…", 16); last = label(state, "", 13);
        section("1  ·  选择入口");
        target = spinner(new String[] {"滴滴出行 App", "微信里的顺风车小程序", "演示订单（用于自测）"});
        text("微信需先手动进入顺风车小程序的司机订单列表。", 13);
        section("2  ·  筛选条件");
        origin = input("出发地关键词", "例如：杭州，西湖（逗号分隔，任选其一）", false);
        destination = input("目的地关键词", "例如：上海，虹桥；留空表示不限", false);
        day = spinner(new String[] {"日期不限", "今天", "明天"});
        begin = input("出发时间 · 开始", "HH:mm，例如 08:00；留空不限", false);
        end = input("出发时间 · 结束", "HH:mm，例如 11:30；支持跨午夜", false);
        fare = input("最低金额（元）", "0 表示不限", true);
        section("3  ·  刷新设置");
        interval = input("刷新间隔（秒，至少 15）", "推荐 30", true);
        marker = input("订单页必须包含的文字", "默认：顺风车；可改为订单页独有标题", false);
        text("仅检查当前屏幕可见订单。离开目标页面或锁屏会暂停，回到列表后继续。", 13);
        button("保存设置", () -> { save(); });
        section("4  ·  开启权限并运行");
        button("开启通知权限", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
            else {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()); startActivity(i);
            }
        });
        button("开启无障碍服务", () -> new AlertDialog.Builder(this).setTitle("允许读取和刷新订单页面")
            .setMessage(getString(R.string.accessibility_description) + "\n\n进入系统设置后，找到“顺风车订单提醒”并开启。")
            .setPositiveButton("前往设置", (d,w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("取消", null).show());
        button("开始监测并打开入口", () -> {
            if (!save()) return;
            if (!serviceEnabled() || !AppConfig.prefs(this).getBoolean("connected", false)) { toast("请先开启无障碍服务，等待连接后再开始"); return; }
            if (!Notices.allowed(this)) { toast("请先允许通知，并启用“匹配订单提醒”通知类别"); return; }
            AppConfig.prefs(this).edit().putBoolean("monitoring", true).putLong("session", SystemClock.elapsedRealtime()).apply();
            AppConfig.status(this, "等待目标应用的订单列表"); Notices.status(this, "打开顺风车司机订单列表即可运行"); openTarget();
        });
        button("停止监测", () -> { AppConfig.stop(this); renderStatus(); toast("已停止刷新和提醒"); });
        section("识别预览");
        text("首次使用先做预览，并核对起点、终点、时间和金额。捕获后返回本应用查看。", 13);
        button("捕获目标页面（30 秒内）", () -> {
            if (!save()) return;
            if (!serviceEnabled() || !AppConfig.prefs(this).getBoolean("connected", false)) { toast("请先开启无障碍服务"); return; }
            AppConfig.prefs(this).edit().putLong("previewUntil", SystemClock.elapsedRealtime() + 30000).putString("preview", "等待捕获，请在 30 秒内打开订单列表…").apply(); openTarget();
        });
        preview = text("尚未捕获页面", 13); preview.setTextIsSelectable(true);
        text("没有起点/终点标签时，按预览中每张订单的编号指定地址行；两项填 0 使用自动识别。请核对多张订单的行号是否一致。", 13);
        originLine = input("起点在订单中的行号", "0：自动；例如 1", true);
        destinationLine = input("终点在订单中的行号", "0：自动；例如 2", true);
        button("保存页面适配", () -> { save(); });
        button("清除预览和匹配记录", () -> {
            AppConfig.prefs(this).edit().remove("preview").remove("lastMatch").remove("lastRefresh").putLong("previewUntil", 0).apply(); renderStatus();
        });
        text("版本 0.1 · 本机处理，无需 Root\n支持文字标签、路线箭头或手动指定地址行。纯图片、订单分组变化或复杂时间区间的页面需要单独适配。", 12);
        load();
    }
    @Override protected void onResume() { super.onResume(); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName wanted = new ComponentName(this, OrderAccessibilityService.class);
        if (enabled != null) for (String component : enabled.split(":")) if (wanted.equals(ComponentName.unflattenFromString(component))) return true;
        return false;
    }
    private void renderStatus() {
        SharedPreferences p = AppConfig.prefs(this);
        status.setText((p.getBoolean("monitoring", false) ? "●  " : "○  ") + p.getString("status", "尚未开始"));
        last.setText("无障碍：" + (serviceEnabled() ? "已开启" : "未开启") + "  ·  通知：" + (Notices.allowed(this) ? "已允许" : "未允许")
            + "\n上次刷新：" + p.getString("lastRefresh", "暂无") + "\n上次匹配：" + p.getString("lastMatch", "暂无"));
        preview.setText(p.getString("preview", "尚未捕获页面"));
    }
    private void load() {
        SharedPreferences p = AppConfig.prefs(this);
        target.setSelection(p.getInt("target", 0)); day.setSelection(p.getInt("day", 0));
        origin.setText(p.getString("origin", "")); destination.setText(p.getString("destination", ""));
        begin.setText(p.getString("begin", "")); end.setText(p.getString("end", ""));
        fare.setText(String.valueOf(Double.longBitsToDouble(p.getLong("fare", Double.doubleToLongBits(0)))));
        interval.setText(String.valueOf(p.getInt("interval", 30))); marker.setText(p.getString("marker", "顺风车"));
        originLine.setText(String.valueOf(p.getInt("originLine", 0))); destinationLine.setText(String.valueOf(p.getInt("destinationLine", 0)));
    }
    private boolean save() {
        try {
            String b = value(begin), e = value(end);
            if (b.isEmpty() != e.isEmpty()) throw new IllegalArgumentException("开始和结束时间需同时填写，或同时留空");
            if (!b.isEmpty()) { b = parseTime(b).toString(); e = parseTime(e).toString(); }
            double f = value(fare).isEmpty() ? 0 : Double.parseDouble(value(fare));
            if (!Double.isFinite(f) || f < 0 || f > 100000) throw new IllegalArgumentException("金额需在 0–100000 元之间");
            int seconds = Integer.parseInt(value(interval));
            if (seconds < 15 || seconds > 3600) throw new IllegalArgumentException("刷新间隔需在 15–3600 秒之间");
            String m = value(marker); if (m.isEmpty()) throw new IllegalArgumentException("请填写订单页面独有的文字标识");
            int fromLine = Integer.parseInt(value(originLine)), toLine = Integer.parseInt(value(destinationLine));
            if (fromLine < 0 || toLine < 0 || fromLine > 30 || toLine > 30 || (fromLine == 0) != (toLine == 0) || (fromLine > 0 && fromLine == toLine))
                throw new IllegalArgumentException("地址行号需同时为 0，或填写两个不同的 1–30 行号");
            AppConfig.prefs(this).edit().putInt("target", target.getSelectedItemPosition()).putInt("day", day.getSelectedItemPosition())
                .putString("origin", value(origin)).putString("destination", value(destination)).putString("begin", b).putString("end", e)
                .putLong("fare", Double.doubleToLongBits(f)).putInt("interval", seconds).putString("marker", m)
                .putInt("originLine", fromLine).putInt("destinationLine", toLine).apply();
            toast("设置已保存"); return true;
        } catch (RuntimeException error) { toast(error instanceof NumberFormatException ? "金额和间隔请填写有效数字" : error.getMessage()); return false; }
    }
    private LocalTime parseTime(String s) {
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("时间请按 HH:mm 填写，例如 08:00，不能填 24:00"); }
    }
    private void openTarget() {
        Intent i = AppConfig.target(this).equals(getPackageName()) ? new Intent(this, DemoActivity.class)
            : getPackageManager().getLaunchIntentForPackage(AppConfig.target(this));
        if (i == null) { toast("未找到所选应用，请先安装或改用另一个入口"); return; }
        try { startActivity(i); } catch (RuntimeException e) { toast("无法打开目标应用，请手动打开"); }
    }
    private String value(EditText edit) { return edit.getText().toString().trim(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void title(String text, int size) { TextView t = text(text, size); t.setTypeface(null, Typeface.BOLD); }
    private TextView text(String value, int size) { return label(content, value, size); }
    private TextView label(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(31,49,58)); t.setLineSpacing(dp(3), 1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(9); parent.addView(t, lp); return t;
    }
    private void section(String value) { TextView t = text(value, 18); t.setTypeface(null, Typeface.BOLD); t.setPadding(0, dp(18), 0, dp(5)); }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(16), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); c.setBackground(bg);
        content.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c;
    }
    private EditText input(String name, String hint, boolean number) {
        text(name, 14); EditText e = new EditText(this); e.setTextSize(16); e.setSingleLine(true); e.setHint(hint);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin = dp(10); content.addView(e, lp); return e;
    }
    private Spinner spinner(String[] labels) {
        Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); content.addView(s, new LinearLayout.LayoutParams(-1, dp(52))); return s;
    }
    private interface Action { void run(); }
    private void button(String label, Action action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(v -> action.run());
        content.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
