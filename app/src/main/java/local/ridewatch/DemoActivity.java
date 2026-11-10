package local.ridewatch;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalTime;

/** Synthetic accessible cards for testing refresh, matching, notifications and stopping. */
public final class DemoActivity extends Activity {
    private int count;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);if(saved!=null)count=saved.getInt("count",0);
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,16));root.setBackgroundColor(Ui.BG);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left+Ui.dp(this,20),b.top+Ui.dp(this,20),b.right+Ui.dp(this,20),b.bottom+Ui.dp(this,16));return insets;});root.requestApplyInsets();}else root.setFitsSystemWindows(true);
        LinearLayout heading=Ui.row(this);Ui.weight(heading,Ui.text(this,"演示顺风车",23,Ui.INK,true));Ui.add(heading,Ui.button(this,"返回助手",false,this::finish),-2,-2);Ui.add(root,heading,-1,-2);Ui.gap(root,14);
        Ui.add(root,Ui.badge(this,"可接订单 · 以下都是测试数据",Ui.ORANGE,0xFFFFF1DF),-1,-2);Ui.gap(root,16);
        LinearLayout refreshRow=Ui.row(this);TextView stamp=Ui.text(this,"自动刷新 "+count+" 次",15,Ui.MUTED,false);Ui.weight(refreshRow,stamp);
        Ui.add(refreshRow,Ui.button(this,"刷新",false,()->stamp.setText("自动刷新 "+(++count)+" 次 · "+LocalTime.now().withNano(0))),-2,-2);Ui.add(root,refreshRow,-1,-2);Ui.gap(root,12);
        ScrollView scroll=new ScrollView(this);LinearLayout list=Ui.column(this);
        Ui.add(list,order("杭州西湖","上海虹桥","今天 09:30","128元",true),-1,-2);Ui.gap(list,14);
        Ui.add(list,order("杭州余杭","嘉兴南湖","明天 23:30","68元",false),-1,-2);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Ui.gap(root,12);
        Ui.add(root,Ui.text(this,"匹配后查看手机通知。返回助手可停止或退出演示。",13,Ui.MUTED,false),-1,-2);
    }
    private LinearLayout order(String from,String to,String time,String fare,boolean example){
        LinearLayout card=Ui.card(this);Ui.add(card,Ui.badge(this,example?"示例：杭州到上海":"示例：另一条路线",example?Ui.TEAL:Ui.MUTED,example?Ui.PALE:Ui.BG),-2,-2);Ui.gap(card,18);
        line(card,"从",from,Ui.TEAL);Ui.gap(card,16);line(card,"到",to,Ui.ORANGE);Ui.gap(card,20);
        LinearLayout bottom=Ui.row(this);Ui.weight(bottom,Ui.text(this,time,15,Ui.MUTED,false));Ui.add(bottom,Ui.text(this,fare,24,Ui.INK,true),-2,-2);Ui.add(card,bottom,-1,-2);
        // Expose one stable, labelled card rather than duplicate visual and accessibility text.
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);card.setFocusable(true);
        card.setContentDescription("出发地："+from+"\n目的地："+to+"\n"+time+" 出发\n"+fare);
        for(int i=0;i<card.getChildCount();i++)card.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);return card;
    }
    private void line(LinearLayout parent,String prefix,String value,int color){LinearLayout r=Ui.row(this);Ui.add(r,Ui.badge(this,prefix,color,prefix.equals("从")?Ui.PALE:0xFFFFF1DF),Ui.dp(this,36),-2);Ui.hgap(r,12);Ui.weight(r,Ui.text(this,value,20,Ui.INK,true));Ui.add(parent,r,-1,-2);}
    @Override public void onSaveInstanceState(Bundle out){out.putInt("count",count);super.onSaveInstanceState(out);}
}
