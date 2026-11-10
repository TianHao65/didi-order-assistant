package local.ridewatch;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalTime;

/** Explicitly labelled synthetic orders; no Didi API or real order submissions. */
public final class DemoActivity extends Activity {
    private int count;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setPadding(24,48,24,32);
        TextView heading = new TextView(this); heading.setText("演示顺风车 · 可接订单"); heading.setTextSize(24); outer.addView(heading);
        TextView stamp = new TextView(this); stamp.setText("这是测试数据，请勿当作真实订单"); outer.addView(stamp);
        Button refresh = new Button(this); refresh.setText("刷新"); refresh.setOnClickListener(v -> stamp.setText("演示刷新 " + (++count) + " 次 · " + LocalTime.now().withNano(0)));
        outer.addView(refresh);
        ScrollView scroll = new ScrollView(this); LinearLayout orders = new LinearLayout(this); orders.setOrientation(LinearLayout.VERTICAL);
        orders.addView(order("出发地：杭州西湖", "目的地：上海虹桥", "今天 09:30 出发", "预计收入 128元"));
        orders.addView(order("出发地：杭州余杭", "目的地：嘉兴南湖", "明天 23:30 出发", "预计收入 68元"));
        scroll.addView(orders); outer.addView(scroll); setContentView(outer);
    }
    private LinearLayout order(String... lines) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(18,24,18,24);
        for (String line : lines) { TextView text = new TextView(this); text.setText(line); text.setTextSize(19); text.setPadding(0,8,0,8); card.addView(text); }
        return card;
    }
}
