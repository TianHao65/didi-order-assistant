package local.ridewatch;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalTime;

final class AppConfig {
    static final String[] PACKAGES = {"com.sdu.didi.psnger", "com.tencent.mm", "local.ridewatch"};
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static String target(Context c) { return PACKAGES[Math.max(0, Math.min(2, prefs(c).getInt("target", 0)))]; }
    static String marker(Context c) { return prefs(c).getString("marker", "顺风车").trim(); }
    static int interval(Context c) { return Math.max(15, prefs(c).getInt("interval", 30)); }
    static OrderEngine.Rule rule(Context c) {
        SharedPreferences p = prefs(c);
        OrderEngine.Rule r = new OrderEngine.Rule();
        r.origin = p.getString("origin", "").trim(); r.destination = p.getString("destination", "").trim();
        r.minimumFare = Double.longBitsToDouble(p.getLong("fare", Double.doubleToLongBits(0)));
        r.day = p.getInt("day", 0);
        String begin = p.getString("begin", ""), end = p.getString("end", "");
        if (!begin.isEmpty() && !end.isEmpty()) { r.start = LocalTime.parse(begin); r.end = LocalTime.parse(end); }
        return r;
    }
    static void status(Context c, String status) { prefs(c).edit().putString("status", status).apply(); }
    static void stop(Context c) { prefs(c).edit().putBoolean("monitoring", false).apply(); Notices.cancelStatus(c); status(c, "已停止"); }
    private AppConfig() {}
}
