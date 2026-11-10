package local.ridewatch;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.List;

final class Notices {
    static final int STATUS = 1, MATCH = 2;
    static void channels(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        NotificationChannel match = new NotificationChannel("matches", "匹配订单提醒", NotificationManager.IMPORTANCE_HIGH);
        match.setDescription("符合路线、时间和金额条件的可见订单"); match.enableVibration(true);
        n.createNotificationChannel(match);
        n.createNotificationChannel(new NotificationChannel("monitor", "刷新运行状态", NotificationManager.IMPORTANCE_LOW));
    }
    static boolean allowed(Context c) {
        return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled()
            && c.getSystemService(NotificationManager.class).getNotificationChannel("matches").getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static PendingIntent app(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void status(Context c, String text) {
        if (!allowed(c)) return;
        PendingIntent stop = PendingIntent.getBroadcast(c, 0, new Intent(c, StopReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(c, "monitor")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("顺风车订单监测中").setContentText(text)
            .setContentIntent(app(c)).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "停止监测", stop).build()).build();
        c.getSystemService(NotificationManager.class).notify(STATUS, notification);
    }
    static void matches(Context c, List<OrderEngine.Order> orders) {
        if (!allowed(c) || orders.isEmpty()) return;
        Intent intent = AppConfig.target(c).equals(c.getPackageName()) ? new Intent(c, DemoActivity.class)
            : c.getPackageManager().getLaunchIntentForPackage(AppConfig.target(c));
        if (intent == null) intent = new Intent(c, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(c, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < Math.min(5, orders.size()); i++) body.append(orders.get(i).summary()).append("\n\n");
        Notification n = new Notification.Builder(c, "matches").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("发现 " + orders.size() + " 条匹配订单")
            .setContentText(orders.get(0).summary()).setStyle(new Notification.BigTextStyle().bigText(body.toString().trim()))
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_EVENT).build();
        c.getSystemService(NotificationManager.class).notify(MATCH, n);
    }
    static void cancelStatus(Context c) { c.getSystemService(NotificationManager.class).cancel(STATUS); }
    private Notices() {}
}
