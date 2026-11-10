package local.ridewatch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class OrderAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();
    private long lastRefresh, lastScan;
    private long session;
    private boolean connected;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() { inspect(true); handler.postDelayed(this, 1500); }
    };
    private final Runnable changed = () -> inspect(false);
    @Override protected void onServiceConnected() {
        connected = true; Notices.channels(this);
        // Reconnection requires a deliberate start; prevents unattended restart after a reboot.
        AppConfig.stop(this);
        AppConfig.prefs(this).edit().putBoolean("connected", true).putLong("previewUntil", 0).apply();
        handler.post(heartbeat);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null || !e.getPackageName().toString().equals(AppConfig.target(this))) return;
        handler.removeCallbacks(changed); handler.postDelayed(changed, 600);
    }
    @Override public void onInterrupt() { AppConfig.stop(this); }
    @Override public void onDestroy() {
        connected = false; handler.removeCallbacksAndMessages(null); AppConfig.stop(this);
        AppConfig.prefs(this).edit().putBoolean("connected", false).apply(); super.onDestroy();
    }
    private void state(String value, boolean running) {
        SharedPreferences p = AppConfig.prefs(this);
        if (!value.equals(p.getString("status", ""))) {
            AppConfig.status(this, value); if (running) Notices.status(this, value);
        }
    }
    private void inspect(boolean allowRefresh) {
        SharedPreferences p = AppConfig.prefs(this);
        boolean running = p.getBoolean("monitoring", false);
        boolean preview = p.getLong("previewUntil", 0) > SystemClock.elapsedRealtime();
        if (!connected || (!running && !preview)) { Notices.cancelStatus(this); return; }
        if (session != p.getLong("session", 0)) { session = p.getLong("session", 0); seen.clear(); lastRefresh = 0; }
        if (!getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            state("屏幕关闭或锁屏，等待回到订单列表", running); return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { state("等待可读取的订单页面", running); return; }
        try {
            if (root.getPackageName() == null || !root.getPackageName().toString().equals(AppConfig.target(this))) {
                state("等待目标应用的顺风车订单列表", running); return;
            }
            OrderEngine.Node snapshot = snapshot(root, 0, new int[] {0});
            String page = String.join("\n", snapshot.lines());
            if (page.isEmpty() || !page.contains(AppConfig.marker(this))) {
                state("未检测到页面标识：" + AppConfig.marker(this), running); return;
            }
            if (page.contains("确认接单") || page.contains("确认抢单") || page.contains("支付密码")) {
                state("当前是确认或付款页面，刷新暂停", running); return;
            }
            List<OrderEngine.Order> orders = OrderEngine.parse(snapshot, LocalDate.now(), p.getInt("originLine", 0), p.getInt("destinationLine", 0), false);
            if (preview) {
                StringBuilder report = new StringBuilder("识别到 " + orders.size() + " 条订单\n");
                List<OrderEngine.Order> candidates = OrderEngine.parse(snapshot, LocalDate.now(), p.getInt("originLine", 0), p.getInt("destinationLine", 0), true);
                for (OrderEngine.Order order : candidates) {
                    report.append("\n").append(order.summary()).append("\n");
                    String[] lines = order.raw.split("\n");
                    for (int i = 0; i < lines.length; i++) report.append(i + 1).append(". ").append(lines[i]).append("\n");
                }
                if (candidates.isEmpty()) report.append("\n页面原始文字（仅本机保存）\n").append(page.substring(0, Math.min(page.length(), 7000)));
                p.edit().putString("preview", report.toString()).putLong("previewUntil", 0).apply();
                if (!running) { state("预览已捕获，返回本应用查看", false); return; }
            }
            boolean listPage = !orders.isEmpty() || page.contains("暂无订单") || page.contains("暂无顺路乘客")
                || page.contains("可接订单") || page.contains("顺路乘客") || page.contains("乘客订单");
            if (!listPage) { state("未识别到订单列表，请先检查识别预览", running); return; }
            long now = SystemClock.elapsedRealtime();
            if (now - lastScan > 1000) {
                lastScan = now;
                OrderEngine.Rule rule = AppConfig.rule(this);
                List<OrderEngine.Order> fresh = new ArrayList<>();
                seen.entrySet().removeIf(e -> now - e.getValue() > 30 * 60 * 1000L);
                for (OrderEngine.Order order : orders) if (rule.matches(order, LocalDate.now()) && !seen.containsKey(order.key())) {
                    if (Notices.allowed(this)) { fresh.add(order); seen.put(order.key(), now); }
                }
                while (seen.size() > 300) seen.remove(seen.keySet().iterator().next());
                if (!fresh.isEmpty()) { Notices.matches(this, fresh); p.edit().putString("lastMatch", LocalTime.now().withNano(0) + " · " + fresh.get(0).summary()).apply(); }
                state("当前识别 " + orders.size() + " 条订单 · 每 " + AppConfig.interval(this) + " 秒刷新", running);
            }
            if (running && allowRefresh && now - lastRefresh >= AppConfig.interval(this) * 1000L) {
                lastRefresh = now;
                if (clickRefresh(root) || pullRefresh(root)) {
                    p.edit().putString("lastRefresh", LocalTime.now().withNano(0).toString()).apply();
                    handler.removeCallbacks(changed); handler.postDelayed(changed, 1500);
                } else state("未找到刷新按钮或可下拉列表，请检查页面", running);
            }
        } catch (RuntimeException error) {
            // Avoid crash loops or interacting with a changing accessibility tree.
            state("页面正在变化或识别失败，请检查预览", running);
        } finally { root.recycle(); }
    }
    private OrderEngine.Node snapshot(AccessibilityNodeInfo n, int depth, int[] count) {
        String clazz = n.getClassName() == null ? "" : n.getClassName().toString();
        boolean list = n.isScrollable() || clazz.contains("RecyclerView") || clazz.contains("ListView") || clazz.contains("ScrollView");
        String text = n.getText() == null ? "" : n.getText().toString();
        if (text.isEmpty() && n.getContentDescription() != null) text = n.getContentDescription().toString();
        OrderEngine.Node node = new OrderEngine.Node(n.isVisibleToUser() ? text : "", list);
        if (depth >= 28 || ++count[0] > 2000) return node;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child == null) continue;
            try { if (child.isVisibleToUser()) node.add(snapshot(child, depth + 1, count)); } finally { child.recycle(); }
        }
        return node;
    }
    private boolean clickRefresh(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText("刷新");
        boolean done = false;
        for (AccessibilityNodeInfo n : found) {
            try {
                CharSequence label = n.getText() != null ? n.getText() : n.getContentDescription();
                if (done || label == null || !label.toString().trim().equals("刷新") || !n.isVisibleToUser() || !n.isEnabled()) continue;
                if (n.isClickable()) done = n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                else {
                    AccessibilityNodeInfo parent = n.getParent();
                    if (parent != null) try {
                        if (parent.isClickable() && parent.isEnabled() && parent.isVisibleToUser() && parent.getChildCount() == 1)
                            done = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    } finally { parent.recycle(); }
                }
            } finally { n.recycle(); }
        }
        return done;
    }
    private boolean pullRefresh(AccessibilityNodeInfo root) {
        Rect bounds = new Rect(); findList(root, bounds, 0);
        if (bounds.height() < 250 || bounds.width() < 150) return false;
        Path path = new Path(); float x = bounds.exactCenterX();
        path.moveTo(x, bounds.top + bounds.height() * 0.18f);
        path.lineTo(x, bounds.top + bounds.height() * 0.65f);
        return dispatchGesture(new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 550)).build(), null, null);
    }
    private void findList(AccessibilityNodeInfo n, Rect best, int depth) {
        if (depth > 25) return;
        String c = n.getClassName() == null ? "" : n.getClassName().toString();
        if (n.isVisibleToUser() && (n.isScrollable() || c.contains("RecyclerView") || c.contains("ListView"))) {
            Rect b = new Rect(); n.getBoundsInScreen(b);
            if ((long) b.width() * b.height() > (long) best.width() * best.height()) best.set(b);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child != null) try { findList(child, best, depth + 1); } finally { child.recycle(); }
        }
    }
}
