package local.ridewatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private LinearLayout content,dock;
    private ScrollView home;
    private String signature="";
    private Ui.Sheet permissions,inspection;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable pulse=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1500);}};
    private static final String[] CONFIG={"target","origin","destination","day","begin","end","fare","interval","marker","originLine","destinationLine"};
    private SharedPreferences p(){return AppConfig.prefs(this);}
    private String s(String key){return p().getString(key,"");}
    private int dp(float n){return Ui.dp(this,n);}
    private TextView text(String v,int size,int color,boolean bold){return Ui.text(this,v,size,color,bold);}
    private void label(LinearLayout parent,String v,int size,int color,boolean bold){Ui.add(parent,text(v,size,color,bold),-1,-2);}
    private void gap(LinearLayout parent,int n){Ui.gap(parent,n);}
    private boolean demo(){return p().contains("demoBackup");}
    private double minimum(){return Double.longBitsToDouble(p().getLong("fare",Double.doubleToLongBits(0)));}
    private String amount(double v){return new java.text.DecimalFormat("0.##").format(v);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Notices.channels(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        home=new ScrollView(this);home.setClipToPadding(false);content=Ui.column(this);content.setPadding(dp(20),dp(20),dp(20),dp(18));home.addView(content);
        root.addView(home,new LinearLayout.LayoutParams(-1,0,1));dock=Ui.column(this);dock.setPadding(dp(20),dp(12),dp(20),dp(12));dock.setBackgroundColor(Ui.WHITE);dock.setElevation(dp(7));Ui.add(root,dock,-1,-2);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});root.requestApplyInsets();
        }else root.setFitsSystemWindows(true);
        buildHome();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(pulse);handler.post(pulse);}
    @Override protected void onPause(){handler.removeCallbacks(pulse);super.onPause();}
    private void refresh(){
        String now=p().getAll().toString()+serviceEnabled()+Notices.allowed(this);
        if(!now.equals(signature)){signature=now;buildHome();if(permissions!=null&&permissions.dialog.isShowing())renderPermissions();if(inspection!=null&&inspection.dialog.isShowing())fillInspection(inspection);}
    }
    private void changed(){signature="";refresh();}
    private void saveRule(SharedPreferences.Editor edit){
        if(p().getBoolean("monitoring",false))AppConfig.stop(this);
        edit.remove("previewOrders").putLong("previewUntil",0).apply();changed();
    }
    private void buildHome(){
        int y=home.getScrollY();content.removeAllViews();dock.removeAllViews();
        LinearLayout top=Ui.row(this);Ui.weight(top,text("顺风车提醒",19,Ui.INK,true));
        Button tutorial=Ui.button(this,demo()?"退出演示":"新手演示",false,demo()?this::exitDemo:this::startDemo);tutorial.setTextSize(13);Ui.add(top,tutorial,-2,-2);Ui.add(content,top,-1,-2);gap(content,12);
        if(demo()){Ui.add(content,Ui.badge(this,"演示模式 · 这里都是测试订单",Ui.ORANGE,0xFFFFF1DF),-1,-2);gap(content,14);}
        LinearLayout hero=Ui.row(this);Ui.weight(hero,text("选路线  →  选时间  →  点开始",14,Ui.MUTED,false));Ui.add(hero,new Ui.MapArt(this),dp(36),dp(36));Ui.add(content,hero,-1,-2);gap(content,12);
        stateCard();gap(content,16);
        section("从哪里看订单？");
        LinearLayout apps=Ui.row(this);int selected=p().getInt("target",0);
        Ui.weight(apps,appChoice("滴滴出行","手机 App","phone",Ui.ORANGE,selected==0,0));Ui.hgap(apps,10);
        Ui.weight(apps,appChoice("微信小程序","微信内打开","chat",Ui.TEAL,selected==1,1));Ui.add(content,apps,-1,-2);gap(content,16);
        routeCard();gap(content,14);conditionsCard();gap(content,14);
        if(!s("lastMatch").isEmpty())lastOrderCard();
        LinearLayout more=Ui.row(this);Ui.weight(more,Ui.button(this,"识别检查",false,this::showInspection));Ui.hgap(more,10);Ui.weight(more,Ui.button(this,"更多设置",false,this::showAdvanced));Ui.add(content,more,-1,-2);gap(content,10);
        TextView privacy=text("条件自动保存 · 订单信息只留在本机",12,Ui.MUTED,false);privacy.setGravity(android.view.Gravity.CENTER);Ui.add(content,privacy,-1,-2);
        boolean running=p().getBoolean("monitoring",false);
        Ui.add(dock,Ui.button(this,running?"回到订单列表":demo()?"开始体验演示":"开始找顺路订单",true,running?this::openTarget:this::start),-1,-2);
        if(running){gap(dock,6);Button stop=Ui.button(this,"停止找单",false,()->{AppConfig.stop(this);changed();});stop.setTextColor(Ui.RED);Ui.add(dock,stop,-1,-2);}
        gap(dock,7);TextView hint=text(running?"保持亮屏，停留在司机订单列表":"找到合适的订单后，手机通知你",12,Ui.MUTED,false);hint.setGravity(android.view.Gravity.CENTER);Ui.add(dock,hint,-1,-2);
        home.post(()->home.scrollTo(0,y));
    }
    private void section(String title){label(content,title,15,Ui.MUTED,true);gap(content,10);}
    private LinearLayout appChoice(String title,String sub,String icon,int tint,boolean selected,int index){
        LinearLayout card=Ui.card(this);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(Ui.background(this,selected?Ui.PALE:Ui.WHITE,18,selected?Ui.TEAL:Ui.LINE));
        LinearLayout row=Ui.row(this);Ui.icon(row,icon,tint,22);Ui.hgap(row,9);LinearLayout names=Ui.column(this);label(names,title,15,Ui.INK,true);gap(names,3);label(names,sub,11,Ui.MUTED,false);Ui.weight(row,names);if(selected)Ui.icon(row,"check",Ui.TEAL,16);Ui.add(card,row,-1,-2);
        Ui.touch(card,()->{if(demo())exitDemo();saveRule(p().edit().putInt("target",index));});card.setContentDescription(title+(selected?"，已选择":"，点击选择"));card.setSelected(selected);return card;
    }
    private void stateCard(){
        boolean running=p().getBoolean("monitoring",false);int ready=(Notices.allowed(this)?1:0)+(serviceEnabled()?1:0);
        if(!running){LinearLayout compact=Ui.row(this);compact.setPadding(dp(12),dp(10),dp(12),dp(10));compact.setBackground(Ui.background(this,ready==2?Ui.PALE:0xFFFFF5E8,14,0));Ui.icon(compact,ready==2?"check":"shield",ready==2?Ui.TEAL:Ui.ORANGE,20);Ui.hgap(compact,9);Ui.weight(compact,text(ready==2?"权限已准备好，点击开始即可":"开始前，还需开启 "+(2-ready)+" 项权限",13,Ui.INK,true));Ui.icon(compact,"chevron",Ui.MUTED,16);Ui.touch(compact,()->showPermissions(false));Ui.add(content,compact,-1,-2);return;}
        String raw=s("status"),title,detail;int color=Ui.TEAL;
        if(!running){title=ready==2?"准备好了，随时开始":"首次使用，完成下面两项权限";detail=ready==2?"选好路线后，点击底部的开始按钮":"每项只需开启一次，页面会显示完成状态";if(ready!=2)color=Ui.ORANGE;}
        else if(raw.contains("屏幕关闭")){title="已暂停，等你解锁手机";detail="亮屏并回到订单列表后会继续";color=Ui.ORANGE;}
        else if(raw.startsWith("当前识别")){title="正在为你找顺路订单";detail="每 "+AppConfig.interval(this)+" 秒刷新 · 当前读到 "+p().getInt("visibleCount",0)+" 条";}
        else if(raw.contains("未检测")||raw.contains("未识别")||raw.contains("未找到")||raw.contains("失败")){title="还没读到正确的订单页面";detail="确认在司机列表，或点击“识别检查”";color=Ui.ORANGE;}
        else if(raw.contains("确认")||raw.contains("付款")){title="正在等待你处理当前页面";detail="返回订单列表后继续找单";color=Ui.ORANGE;}
        else{title="已开启，等待你打开订单列表";detail=p().getInt("target",0)==1?"进入微信小程序里的司机订单列表":"进入顺风车，打开司机订单列表";}
        LinearLayout box=Ui.card(this);box.setBackground(Ui.background(this,color==Ui.ORANGE?0xFFFFF5E8:Ui.PALE,20,0));
        LinearLayout r=Ui.row(this);Ui.icon(r,running?"bell":ready==2?"check":"shield",color,24);Ui.hgap(r,10);Ui.weight(r,text(title,16,Ui.INK,true));Ui.add(box,r,-1,-2);gap(box,8);label(box,detail,13,Ui.MUTED,false);
        if(running&&!s("lastRefresh").isEmpty()){gap(box,8);label(box,"上次刷新 "+s("lastRefresh"),12,Ui.MUTED,false);}Ui.add(content,box,-1,-2);
    }
    private void routeCard(){
        LinearLayout card=Ui.card(this),heading=Ui.row(this);card.setPadding(dp(16),dp(14),dp(16),dp(14));Ui.weight(heading,text("我想接的路线",15,Ui.MUTED,true));
        Ui.add(heading,Ui.badge(this,"点击修改",Ui.TEAL,Ui.PALE),-2,-2);Ui.add(card,heading,-1,-2);gap(card,12);
        routeLine(card,"从",s("origin").isEmpty()?"选择出发城市或区域":s("origin"),Ui.TEAL,s("origin").isEmpty());gap(card,12);
        routeLine(card,"到",s("destination").isEmpty()?"选择目的城市或区域":s("destination"),Ui.ORANGE,s("destination").isEmpty());Ui.touch(card,this::showRoute);card.setContentDescription("修改路线，从"+(s("origin").isEmpty()?"不限":s("origin"))+"到"+(s("destination").isEmpty()?"不限":s("destination")));Ui.add(content,card,-1,-2);
    }
    private void routeLine(LinearLayout parent,String prefix,String value,int tint,boolean placeholder){
        LinearLayout r=Ui.row(this);Ui.add(r,Ui.badge(this,prefix,tint,prefix.equals("从")?Ui.PALE:0xFFFFF1DF),dp(38),-2);Ui.hgap(r,12);Ui.weight(r,text(value,18,placeholder?Ui.MUTED:Ui.INK,!placeholder));Ui.add(parent,r,-1,-2);
    }
    private String timeSummary(){String day=new String[]{"日期不限","今天","明天"}[Math.max(0,Math.min(2,p().getInt("day",0)))];return day+" · "+(s("begin").isEmpty()?"全天":s("begin")+" — "+s("end"));}
    private void conditionsCard(){
        LinearLayout card=Ui.card(this);settingRow(card,"time","出发时间",timeSummary(),this::showTime);gap(card,14);
        View line=new View(this);line.setBackgroundColor(Ui.LINE);Ui.add(card,line,-1,dp(1));gap(card,14);
        settingRow(card,"money","订单金额",minimum()<=0?"金额不限":"至少 "+amount(minimum())+" 元",this::showFare);Ui.add(content,card,-1,-2);
    }
    private void settingRow(LinearLayout card,String icon,String title,String sub,Runnable action){
        LinearLayout r=Ui.row(this);Ui.icon(r,icon,Ui.TEAL,26);Ui.hgap(r,12);LinearLayout t=Ui.column(this);label(t,title,14,Ui.MUTED,false);gap(t,5);label(t,sub,17,Ui.INK,true);Ui.weight(r,t);Ui.icon(r,"chevron",Ui.MUTED,18);Ui.touch(r,action);Ui.add(card,r,-1,-2);
    }
    private boolean serviceEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);ComponentName wanted=new ComponentName(this,OrderAccessibilityService.class);if(enabled!=null)for(String c:enabled.split(":"))if(wanted.equals(ComponentName.unflattenFromString(c)))return true;return false;}
    private void permissionsCard(){
        LinearLayout card=Ui.card(this);boolean all=Notices.allowed(this)&&serviceEnabled();
        if(all){LinearLayout r=Ui.row(this);Ui.icon(r,"check",Ui.TEAL,24);Ui.hgap(r,10);Ui.weight(r,text("运行权限已准备好",15,Ui.INK,true));Ui.touch(r,()->showPermissions(false));Ui.add(card,r,-1,-2);}
        else{label(card,"开始前，完成这 2 步",18,Ui.INK,true);gap(card,14);permissionRow(card,1,"允许消息提醒","好订单来了，通知你",Notices.allowed(this),this::notificationPermission);gap(card,14);permissionRow(card,2,"允许自动刷新","在订单页面帮你刷新",serviceEnabled(),this::accessibilityPermission);}
        Ui.add(content,card,-1,-2);
    }
    private void permissionRow(LinearLayout parent,int index,String title,String hint,boolean ready,Runnable action){
        LinearLayout r=Ui.row(this);Ui.add(r,Ui.badge(this,ready?"✓":String.valueOf(index),ready?Ui.TEAL:Ui.ORANGE,ready?Ui.PALE:0xFFFFF1DF),dp(34),-2);Ui.hgap(r,10);
        LinearLayout t=Ui.column(this);label(t,title,15,Ui.INK,true);gap(t,4);label(t,hint,12,Ui.MUTED,false);Ui.weight(r,t);Ui.hgap(r,8);
        Button b=Ui.button(this,ready?"已开启":"去开启",false,action);b.setTextSize(13);Ui.add(r,b,dp(74),-2);Ui.add(parent,r,-1,-2);
    }
    private void start(){
        if(!Notices.allowed(this)||!serviceEnabled()){showPermissions(true);return;}
        if(!p().getBoolean("connected",false)){toast("自动刷新正在连接，请稍等片刻再开始");return;}
        if(p().getBoolean("skipGuide",false)||demo()){startMonitoring();return;}
        Ui.Sheet sheet=new Ui.Sheet(this,"接下来这样操作");
        guideRow(sheet.body,1,p().getInt("target",0)==1?"打开微信顺风车小程序":"打开滴滴里的顺风车","使用司机 / 车主入口");
        guideRow(sheet.body,2,"进入可接订单列表","停留在列表顶部，方便下拉刷新");
        guideRow(sheet.body,3,"保持手机亮屏","符合条件时通知你，接单由你确认");
        CheckBox skip=new CheckBox(this);skip.setText("以后直接开始");skip.setTextColor(Ui.MUTED);Ui.add(sheet.body,skip,-1,-2);
        sheet.action(this,"去订单列表并开始",()->{p().edit().putBoolean("skipGuide",skip.isChecked()).apply();sheet.dialog.dismiss();startMonitoring();});sheet.show();
    }
    private void guideRow(LinearLayout parent,int number,String title,String desc){
        LinearLayout r=Ui.row(this);Ui.add(r,Ui.badge(this,String.valueOf(number),Ui.TEAL,Ui.PALE),dp(36),-2);Ui.hgap(r,12);LinearLayout t=Ui.column(this);label(t,title,17,Ui.INK,true);gap(t,5);label(t,desc,14,Ui.MUTED,false);Ui.weight(r,t);Ui.add(parent,r,-1,-2);gap(parent,22);
    }
    private void startMonitoring(){
        if(!Notices.allowed(this)||!serviceEnabled()){showPermissions(true);return;}
        if(!p().getBoolean("connected",false)){toast("自动刷新正在连接，请稍后再试");return;}
        if(!installed()){showMissingApp();return;}
        p().edit().putBoolean("monitoring",true).putLong("session",SystemClock.elapsedRealtime()).apply();AppConfig.status(this,"等待目标应用的订单列表");Notices.status(this,"打开顺风车司机订单列表即可运行");changed();openTarget();
    }
    private void showPermissions(boolean forStart){permissions=new Ui.Sheet(this,"先完成两项准备");permissions.dialog.setOnDismissListener(d->permissions=null);permissions.footer.setTag(forStart);renderPermissions();permissions.show();}
    private void renderPermissions(){
        if(permissions==null)return;permissions.body.removeAllViews();
        label(permissions.body,"第一次设置完成后，以后点开始就能使用。",14,Ui.MUTED,false);gap(permissions.body,20);
        permissionRow(permissions.body,1,"允许消息提醒","允许本应用发送手机通知",Notices.allowed(this),this::notificationPermission);gap(permissions.body,20);
        permissionRow(permissions.body,2,"允许自动刷新","系统中开启“顺风车订单提醒”",serviceEnabled(),this::accessibilityPermission);gap(permissions.body,16);
        boolean ready=Notices.allowed(this)&&serviceEnabled();boolean forStart=Boolean.TRUE.equals(permissions.footer.getTag());
        permissions.action(this,ready?(forStart?"准备好了，继续开始":"完成"):!Notices.allowed(this)?"第 1 步：允许消息提醒":"第 2 步：允许自动刷新",()->{
            if(!Notices.allowed(this))notificationPermission();else if(!serviceEnabled())accessibilityPermission();else{Ui.Sheet current=permissions;current.dialog.dismiss();if(forStart)start();}
        });
    }
    private void notificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            if(p().getBoolean("askedNotification",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));return;
            }
            p().edit().putBoolean("askedNotification",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        }else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    private void accessibilityPermission(){
        Ui.Sheet sheet=new Ui.Sheet(this,"开启自动刷新");label(sheet.body,"这项权限让本应用读取你打开的订单文字，并刷新订单列表。",15,Ui.MUTED,false);gap(sheet.body,20);
        guideRow(sheet.body,1,"找到“顺风车订单提醒”","通常在“已安装的应用”或“下载的应用”中");guideRow(sheet.body,2,"打开使用开关","确认后，返回本应用即可");
        label(sheet.body,"只在所选订单页面运行。你可以随时停止。",13,Ui.MUTED,false);
        gap(sheet.body,14);Ui.add(sheet.body,Ui.button(this,"开关被限制？查看办法",false,this::restrictedHelp),-1,-2);
        sheet.action(this,"去系统设置开启",()->{sheet.dialog.dismiss();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));});sheet.show();
    }
    private void restrictedHelp(){
        Ui.Sheet sheet=new Ui.Sheet(this,"开关被限制时这样做");
        label(sheet.body,"部分手机会限制从文件安装的应用使用无障碍。菜单名称可能因品牌不同而变化。",14,Ui.MUTED,false);gap(sheet.body,20);
        guideRow(sheet.body,1,"打开本应用的“应用信息”","点击下方按钮即可前往");
        guideRow(sheet.body,2,"查看右上角菜单","找到“允许受限设置”，按系统提示操作");
        guideRow(sheet.body,3,"再回到自动刷新设置","找到“顺风车订单提醒”并开启");
        sheet.action(this,"打开应用信息",()->{sheet.dialog.dismiss();startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));});sheet.show();
    }
    private EditText edit(LinearLayout parent,String title,String value,String hint,boolean numeric){
        label(parent,title,14,Ui.MUTED,true);gap(parent,8);EditText e=new EditText(this);e.setTextSize(19);e.setTextColor(Ui.INK);e.setHintTextColor(Ui.MUTED);e.setText(value);e.setHint(hint);e.setSingleLine(true);e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);
        e.setBackground(Ui.background(this,Ui.BG,15,Ui.LINE));e.setPadding(dp(14),dp(14),dp(14),dp(14));e.setMinimumHeight(dp(56));Ui.add(parent,e,-1,-2);gap(parent,16);return e;
    }
    private void showRoute(){
        Ui.Sheet sheet=new Ui.Sheet(this,"我想接的路线");
        EditText from=edit(sheet.body,"从哪里出发",s("origin"),"例如：杭州、西湖",false);EditText to=edit(sheet.body,"到哪里去",s("destination"),"例如：上海、虹桥",false);
        Ui.add(sheet.body,Ui.button(this,"交换出发地和目的地",false,()->{String a=from.getText().toString();from.setText(to.getText());to.setText(a);}),-1,-2);gap(sheet.body,16);
        label(sheet.body,"填常去的城市或区域即可。留空表示不限；多个区域用逗号分开。",13,Ui.MUTED,false);
        sheet.action(this,"用这条路线",()->{if(from.length()>100||to.length()>100){toast("地名太长了，请简短填写");return;}saveRule(p().edit().putString("origin",from.getText().toString().trim()).putString("destination",to.getText().toString().trim()));sheet.dialog.dismiss();});sheet.show();
    }
    private void showTime(){
        Ui.Sheet sheet=new Ui.Sheet(this,"什么时候出发？");int[] day={Math.max(0,Math.min(2,p().getInt("day",0)))};boolean[] limited={!s("begin").isEmpty()};
        String[] times={limited[0]?s("begin"):"08:00",limited[0]?s("end"):"11:00"};Runnable[] draw=new Runnable[1];
        draw[0]=()->{
            sheet.body.removeAllViews();label(sheet.body,"选择日期",14,Ui.MUTED,true);gap(sheet.body,10);LinearLayout days=Ui.row(this);String[] names={"日期不限","今天","明天"};
            for(int i=0;i<3;i++){final int index=i;if(i>0)Ui.hgap(days,7);Ui.weight(days,Ui.chip(this,names[i],day[0]==i,()->{day[0]=index;draw[0].run();}));}Ui.add(sheet.body,days,-1,-2);gap(sheet.body,24);
            label(sheet.body,"选择时间",14,Ui.MUTED,true);gap(sheet.body,10);LinearLayout modes=Ui.row(this);Ui.weight(modes,Ui.chip(this,"全天都可以",!limited[0],()->{limited[0]=false;draw[0].run();}));Ui.hgap(modes,8);Ui.weight(modes,Ui.chip(this,"指定时间段",limited[0],()->{limited[0]=true;draw[0].run();}));Ui.add(sheet.body,modes,-1,-2);
            if(limited[0]){
                gap(sheet.body,18);LinearLayout clocks=Ui.row(this);
                for(int i=0;i<2;i++){final int index=i;LinearLayout block=Ui.column(this);label(block,i==0?"最早出发":"最晚出发",13,Ui.MUTED,false);gap(block,7);Button clock=Ui.button(this,times[i],false,()->{
                    LocalTime t=LocalTime.parse(times[index]);new TimePickerDialog(this,(picker,h,m)->{times[index]=String.format(Locale.ROOT,"%02d:%02d",h,m);draw[0].run();},t.getHour(),t.getMinute(),true).show();
                });clock.setTextSize(27);Ui.add(block,clock,-1,-2);if(i>0)Ui.hgap(clocks,12);Ui.weight(clocks,block);}Ui.add(sheet.body,clocks,-1,-2);gap(sheet.body,18);
                label(sheet.body,"快速选择",13,Ui.MUTED,false);gap(sheet.body,8);LinearLayout quick=Ui.row(this);String[] q={"早上","下午","晚上"},a={"06:00","12:00","18:00"},b={"12:00","18:00","23:59"};
                for(int i=0;i<3;i++){final int index=i;if(i>0)Ui.hgap(quick,6);Ui.weight(quick,Ui.chip(this,q[i],times[0].equals(a[i])&&times[1].equals(b[i]),()->{times[0]=a[index];times[1]=b[index];draw[0].run();}));}Ui.add(sheet.body,quick,-1,-2);gap(sheet.body,15);
                label(sheet.body,LocalTime.parse(times[0]).isAfter(LocalTime.parse(times[1]))?"跨午夜：会匹配所选日期的凌晨或夜间，不会自动扩展到次日。":"只提醒在这个时间段出发的订单。",13,Ui.MUTED,false);
            }else{gap(sheet.body,18);label(sheet.body,"不限制出发钟点，只按日期筛选。",14,Ui.MUTED,false);}
        };draw[0].run();sheet.action(this,"用这个时间",()->{saveRule(p().edit().putInt("day",day[0]).putString("begin",limited[0]?times[0]:"").putString("end",limited[0]?times[1]:""));sheet.dialog.dismiss();});sheet.show();
    }
    private void showFare(){
        Ui.Sheet sheet=new Ui.Sheet(this,"订单至少多少钱？");EditText value=edit(sheet.body,"最低金额（元）",amount(minimum()),"0 表示金额不限",true);
        LinearLayout presets=Ui.row(this);double[] choices={0,50,100};String[] names={"不限","50 元","100 元"};
        for(int i=0;i<3;i++){final int n=i;if(i>0)Ui.hgap(presets,7);Ui.weight(presets,Ui.chip(this,names[i],minimum()==choices[i],()->value.setText(amount(choices[n]))));}Ui.add(sheet.body,presets,-1,-2);gap(sheet.body,16);label(sheet.body,"只按订单页面显示的金额判断。",13,Ui.MUTED,false);
        sheet.action(this,"用这个金额",()->{try{double f=value.getText().toString().trim().isEmpty()?0:Double.parseDouble(value.getText().toString());if(!Double.isFinite(f)||f<0||f>100000)throw new NumberFormatException();saveRule(p().edit().putLong("fare",Double.doubleToLongBits(f)));sheet.dialog.dismiss();}catch(RuntimeException e){value.setError("请输入 0–100000 之间的金额");}});sheet.show();
    }
    private void showAdvanced(){
        Ui.Sheet sheet=new Ui.Sheet(this,"更多设置");label(sheet.body,"刷新频率",16,Ui.INK,true);gap(sheet.body,8);label(sheet.body,"默认 30 秒，一般无需修改。",13,Ui.MUTED,false);gap(sheet.body,12);
        LinearLayout options=Ui.row(this);int[] seconds={30,60,15};String[] names={"30 秒\n推荐","60 秒\n省电","15 秒\n较快"};
        for(int i=0;i<3;i++){final int index=i;if(i>0)Ui.hgap(options,7);Ui.weight(options,Ui.chip(this,names[i],AppConfig.interval(this)==seconds[i],()->{saveRule(p().edit().putInt("interval",seconds[index]));sheet.dialog.dismiss();}));}Ui.add(sheet.body,options,-1,-2);gap(sheet.body,22);
        Ui.add(sheet.body,Ui.button(this,"识别不了？检查订单页面",false,()->{sheet.dialog.dismiss();showInspection();}),-1,-2);gap(sheet.body,12);
        LinearLayout troubleshooting=Ui.column(this);troubleshooting.setVisibility(View.GONE);
        Ui.add(sheet.body,Ui.button(this,"调整订单页标题",false,()->troubleshooting.setVisibility(troubleshooting.getVisibility()==View.GONE?View.VISIBLE:View.GONE)),-1,-2);gap(sheet.body,12);
        EditText marker=edit(troubleshooting,"订单列表上方的标题",AppConfig.marker(this),"例如：顺风车",false);
        label(troubleshooting,"页面需要包含这段文字，才会尝试读取和刷新。只在识别失败时修改。",13,Ui.MUTED,false);gap(troubleshooting,12);
        Ui.add(troubleshooting,Ui.button(this,"使用这个标题",false,()->{String m=marker.getText().toString().trim();if(m.isEmpty()){marker.setError("请填写列表标题");return;}saveRule(p().edit().putString("marker",m));sheet.dialog.dismiss();}),-1,-2);Ui.add(sheet.body,troubleshooting,-1,-2);
        Ui.add(sheet.body,Ui.button(this,"恢复自动识别地址",false,()->{saveRule(p().edit().putInt("originLine",0).putInt("destinationLine",0));toast("已恢复自动识别");}),-1,-2);gap(sheet.body,12);
        Ui.add(sheet.body,Ui.button(this,"清除已读取的订单记录",false,()->{p().edit().remove("preview").remove("previewOrders").remove("previewCaptured").remove("lastMatch").remove("lastOrders").remove("lastRefresh").putLong("previewUntil",0).apply();changed();toast("记录已清除");}),-1,-2);gap(sheet.body,20);
        label(sheet.body,"只看当前屏幕的订单。离开列表或锁屏会暂停。纯图片订单暂时无法识别。",13,Ui.MUTED,false);gap(sheet.body,10);label(sheet.body,"版本 0.2.0 · 无需 Root · 本机处理",12,Ui.MUTED,false);sheet.action(this,"完成",sheet.dialog::dismiss);sheet.show();
    }
    private void showInspection(){
        inspection=new Ui.Sheet(this,"订单识别检查");inspection.dialog.setOnDismissListener(d->inspection=null);fillInspection(inspection);inspection.show();
    }
    private void fillInspection(Ui.Sheet sheet){
        sheet.body.removeAllViews();
        JSONArray orders=array(s("previewOrders"));boolean waiting=p().getLong("previewUntil",0)>SystemClock.elapsedRealtime();
        label(sheet.body,waiting?"正在等你打开订单页面":"先确认订单读对，再开始找单",19,Ui.INK,true);gap(sheet.body,10);
        label(sheet.body,waiting?"进入司机订单列表，读到后返回这里查看。":"检查时会打开所选入口。进入司机订单列表，再返回本应用查看结果。",14,Ui.MUTED,false);gap(sheet.body,18);
        if(orders.length()>0){label(sheet.body,"上次检查读到 "+orders.length()+" 条候选订单",13,Ui.MUTED,true);gap(sheet.body,12);for(int i=0;i<Math.min(12,orders.length());i++){JSONObject order=orders.optJSONObject(i);if(order!=null){Ui.add(sheet.body,orderCard(order,true),-1,-2);gap(sheet.body,12);}}}
        else if(p().contains("previewCaptured")){label(sheet.body,"没有读到完整订单",18,Ui.ORANGE,true);gap(sheet.body,8);label(sheet.body,"确认是司机订单列表。如果仍无结果，可以查看读取的文字，或在更多设置中调整列表标题。",14,Ui.MUTED,false);gap(sheet.body,12);}
        if(!s("preview").isEmpty()||orders.length()>0){Ui.add(sheet.body,Ui.button(this,"查看读取的文字",false,()->showRaw(s("preview"))),-1,-2);gap(sheet.body,12);}
        sheet.action(this,waiting?"再打开订单页面":"打开订单页面检查",()->{if(!serviceEnabled()){sheet.dialog.dismiss();accessibilityPermission();return;}if(!p().getBoolean("connected",false)){toast("自动刷新正在连接，请稍等再检查");return;}if(!installed()){showMissingApp();return;}
            p().edit().putLong("previewUntil",SystemClock.elapsedRealtime()+120000).putString("preview","等待检查，请打开司机订单列表…").remove("previewOrders").remove("previewCaptured").apply();sheet.dialog.dismiss();changed();openTarget();});
    }
    private JSONArray array(String raw){try{return new JSONArray(raw);}catch(Exception ignored){return new JSONArray();}}
    private LinearLayout orderCard(JSONObject order,boolean inspection){
        LinearLayout card=Ui.card(this);boolean matched=order.optBoolean("matched"),mapping=order.optBoolean("needsMapping");
        Ui.add(card,Ui.badge(this,matched?"符合你的条件":mapping?"需要确认地址":"不符合当前条件",matched?Ui.TEAL:Ui.ORANGE,matched?Ui.PALE:0xFFFFF1DF),-2,-2);gap(card,14);
        routeLine(card,"从",order.optString("from").isEmpty()?"待选择出发地":order.optString("from"),Ui.TEAL,mapping);gap(card,12);
        routeLine(card,"到",order.optString("to").isEmpty()?"待选择目的地":order.optString("to"),Ui.ORANGE,mapping);gap(card,14);
        String date=order.optString("date"),dateText=date.isEmpty()?"日期未读到":date.equals(LocalDate.now().toString())?"今天":date.equals(LocalDate.now().plusDays(1).toString())?"明天":date;
        label(card,dateText+" "+order.optString("time")+"   ·   "+amount(order.optDouble("fare"))+" 元",15,Ui.INK,true);gap(card,8);label(card,order.optString("reason"),12,Ui.MUTED,false);
        if(inspection&&mapping){gap(card,12);Ui.add(card,Ui.button(this,"点选上车和下车地点",false,()->chooseAddress(order.optString("raw"))),-1,-2);}return card;
    }
    private void lastOrderCard(){
        label(content,"上次发现的合适订单",15,Ui.MUTED,true);gap(content,10);JSONArray orders=array(s("lastOrders"));
        if(orders.length()>0&&orders.optJSONObject(0)!=null)Ui.add(content,orderCard(orders.optJSONObject(0),false),-1,-2);
        else{LinearLayout card=Ui.card(this);label(card,s("lastMatch"),15,Ui.INK,false);Ui.add(content,card,-1,-2);}gap(content,14);
    }
    private void chooseAddress(String raw){
        String[] lines=raw.split("\n");new AlertDialog.Builder(this).setTitle("哪一行是上车地点？").setItems(lines,(d,from)->{
            new AlertDialog.Builder(this).setTitle("哪一行是下车地点？").setItems(lines,(e,to)->{
                OrderEngine.Node card=new OrderEngine.Node(raw,false);
                if(from==to||OrderEngine.parse(card,LocalDate.now(),from+1,to+1,false).isEmpty()){toast("请选择两个不同的地址，不能选择金额或时间");return;}
                new AlertDialog.Builder(this).setTitle("确认这两个地址").setMessage("从："+lines[from]+"\n到："+lines[to]+"\n\n会按相同位置读取其他订单，请再次检查多条订单。")
                    .setPositiveButton("确认并重新检查",(a,b)->{saveRule(p().edit().putInt("originLine",from+1).putInt("destinationLine",to+1));p().edit().putLong("previewUntil",SystemClock.elapsedRealtime()+120000).remove("previewOrders").remove("previewCaptured").apply();openTarget();})
                    .setNegativeButton("重选",(a,b)->chooseAddress(raw)).show();
            }).setNegativeButton("取消",null).show();
        }).setNegativeButton("取消",null).show();
    }
    private void showRaw(String raw){Ui.Sheet sheet=new Ui.Sheet(this,"读取的页面文字");TextView t=text(raw.isEmpty()?"暂无读取结果":raw,13,Ui.MUTED,false);t.setTextIsSelectable(true);Ui.add(sheet.body,t,-1,-2);sheet.action(this,"返回",sheet.dialog::dismiss);sheet.show();}
    private Intent targetIntent(){return AppConfig.target(this).equals(getPackageName())?new Intent(this,DemoActivity.class):getPackageManager().getLaunchIntentForPackage(AppConfig.target(this));}
    private boolean installed(){return targetIntent()!=null;}
    private void showMissingApp(){new AlertDialog.Builder(this).setTitle("没有找到这个入口").setMessage("请先安装滴滴出行或微信，也可以点击首页的“新手演示”体验操作。") .setPositiveButton("知道了",null).show();}
    private void openTarget(){Intent i=targetIntent();if(i==null){showMissingApp();return;}try{startActivity(i);}catch(RuntimeException e){toast("请手动打开所选应用的顺风车订单列表");}}
    private void startDemo(){
        if(!demo()){
            JSONObject backup=new JSONObject();Map<String,?> values=p().getAll();try{for(String key:CONFIG)if(values.containsKey(key))backup.put(key,values.get(key));}catch(Exception ignored){}
            AppConfig.stop(this);p().edit().putString("demoBackup",backup.toString()).putInt("target",2).putString("origin","杭州").putString("destination","上海").putInt("day",1)
                .putString("begin","08:00").putString("end","11:00").putLong("fare",Double.doubleToLongBits(100)).putInt("interval",30).putString("marker","演示顺风车").putInt("originLine",0).putInt("destinationLine",0)
                .remove("lastMatch").remove("lastOrders").remove("lastRefresh").remove("previewOrders").remove("previewCaptured").putLong("previewUntil",0).apply();changed();
        }
        Ui.Sheet sheet=new Ui.Sheet(this,"先体验一次，不用登录");guideRow(sheet.body,1,"我们帮你填好一条路线","杭州 → 上海，今天 08:00–11:00，至少 100 元");guideRow(sheet.body,2,"看一看通知和自动刷新","测试页面里的订单均为演示数据");guideRow(sheet.body,3,"体验完，退出演示","你的原有路线和条件会恢复");sheet.action(this,"开始体验",()->{sheet.dialog.dismiss();start();});sheet.show();
    }
    private void exitDemo(){
        if(!demo())return;AppConfig.stop(this);SharedPreferences.Editor edit=p().edit();
        try{JSONObject backup=new JSONObject(s("demoBackup"));for(String key:CONFIG){if(!backup.has(key))edit.remove(key);else if(key.equals("fare"))edit.putLong(key,backup.getLong(key));else if(key.equals("target")||key.equals("day")||key.equals("interval")||key.endsWith("Line"))edit.putInt(key,backup.getInt(key));else edit.putString(key,backup.getString(key));}}catch(Exception ignored){edit.putInt("target",0).putString("marker","顺风车").putInt("originLine",0).putInt("destinationLine",0);}
        edit.remove("demoBackup").remove("lastMatch").remove("lastOrders").remove("lastRefresh").remove("previewOrders").remove("previewCaptured").remove("preview").putLong("previewUntil",0).apply();changed();
    }
    private void toast(String value){android.widget.Toast.makeText(this,value,android.widget.Toast.LENGTH_LONG).show();}
}
