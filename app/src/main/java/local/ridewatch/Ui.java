package local.ridewatch;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small native UI components; scalable text and touch targets of at least 48 dp. */
final class Ui {
    static final int INK = Color.rgb(25,48,53), MUTED = Color.rgb(97,118,122);
    static final int TEAL = Color.rgb(0,119,108), PALE = Color.rgb(231,246,241);
    static final int BG = Color.rgb(245,248,247), LINE = Color.rgb(225,233,230);
    static final int ORANGE = Color.rgb(227,126,41), RED = Color.rgb(181,61,61);
    static final int WHITE = Color.WHITE;
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static void add(LinearLayout p, View v, int w, int h) { p.addView(v, new LinearLayout.LayoutParams(w,h)); }
    static void weight(LinearLayout p, View v) { p.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1)); }
    static void gap(LinearLayout p, int n) { add(p,new View(p.getContext()),1,dp(p.getContext(),n)); }
    static void hgap(LinearLayout p, int n) { add(p,new View(p.getContext()),dp(p.getContext(),n),1); }
    static GradientDrawable background(Context c, int color, int radius, int border) {
        GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(c,radius));
        if (border != 0) b.setStroke(dp(c,1),border); return b;
    }
    static TextView text(Context c,String value,int size,int color,boolean bold) {
        TextView v=new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setLineSpacing(dp(c,3),1); if(bold)v.setTypeface(null,Typeface.BOLD); return v;
    }
    static LinearLayout card(Context c) {
        LinearLayout v=column(c); int p=dp(c,18); v.setPadding(p,p,p,p); v.setBackground(background(c,WHITE,22,LINE)); return v;
    }
    static Button button(Context c,String label,boolean primary,Runnable action) {
        Button b=new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(null,Typeface.BOLD);
        b.setTextColor(primary?WHITE:TEAL); b.setMinHeight(dp(c,52)); b.setMinimumHeight(dp(c,52)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33FFFFFF:0x1600776C),background(c,primary?TEAL:PALE,16,0),null));
        b.setStateListAnimator(null); b.setOnClickListener(v->action.run()); return b;
    }
    static Button chip(Context c,String label,boolean selected,Runnable action) {
        Button b=button(c,label,false,action); b.setTextSize(14);
        b.setTextColor(selected?TEAL:MUTED);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1600776C),background(c,selected?PALE:BG,13,selected?TEAL:LINE),null));
        b.setSelected(selected); b.setContentDescription(label+(selected?"，已选择":"")); return b;
    }
    static TextView badge(Context c,String value,int foreground,int fill) {
        TextView t=text(c,value,12,foreground,true); t.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6)); t.setBackground(background(c,fill,20,0)); return t;
    }
    static void touch(View v,Runnable action) {
        v.setClickable(true);v.setFocusable(true);v.setOnClickListener(w->action.run());
        v.setMinimumHeight(dp(v.getContext(),52));
    }
    static void icon(LinearLayout row,String kind,int color,int size) {
        add(row,new Icon(row.getContext(),kind,color),dp(row.getContext(),size),dp(row.getContext(),size));
    }
    static final class Icon extends View {
        private final String kind; private final int color; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path a=new Path();
            switch(kind){
                case "check":a.moveTo(5,12);a.lineTo(10,17);a.lineTo(19,7);c.drawPath(a,p);break;
                case "chevron":a.moveTo(9,6);a.lineTo(15,12);a.lineTo(9,18);c.drawPath(a,p);break;
                case "time":c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,16,14,p);break;
                case "bell":a.moveTo(5,17);a.lineTo(7,15);a.lineTo(7,10);a.cubicTo(7,3,17,3,17,10);a.lineTo(17,15);a.lineTo(19,17);a.close();c.drawPath(a,p);c.drawArc(10,18,14,22,0,180,false,p);break;
                case "shield":a.moveTo(12,3);a.lineTo(20,6);a.lineTo(19,14);a.quadTo(18,18,12,22);a.quadTo(6,18,5,14);a.lineTo(4,6);a.close();c.drawPath(a,p);a.reset();a.moveTo(8,12);a.lineTo(11,15);a.lineTo(16,9);c.drawPath(a,p);break;
                case "pin":c.drawCircle(12,9,2.2f,p);a.moveTo(12,22);a.cubicTo(-3,8,5,2,12,2);a.cubicTo(19,2,27,8,12,22);c.drawPath(a,p);break;
                case "swap":c.drawLine(5,8,19,8,p);c.drawLine(5,16,19,16,p);a.moveTo(16,5);a.lineTo(19,8);a.lineTo(16,11);a.moveTo(8,13);a.lineTo(5,16);a.lineTo(8,19);c.drawPath(a,p);break;
                case "play":a.moveTo(8,4);a.lineTo(20,12);a.lineTo(8,20);a.close();c.drawPath(a,p);break;
                case "money":c.drawCircle(12,12,9,p);a.moveTo(8,7);a.lineTo(12,12);a.lineTo(16,7);a.moveTo(8,13);a.lineTo(16,13);a.moveTo(8,16);a.lineTo(16,16);a.moveTo(12,12);a.lineTo(12,19);c.drawPath(a,p);break;
                case "phone":c.drawRoundRect(6,2,18,22,3,3,p);c.drawLine(10,18,14,18,p);break;
                case "chat":c.drawRoundRect(3,4,21,17,5,5,p);a.moveTo(7,17);a.lineTo(5,22);a.lineTo(12,17);c.drawPath(a,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8,10,1,p);c.drawCircle(16,10,1,p);break;
                default:c.drawCircle(12,12,9,p);c.drawLine(12,8,12,13,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,17,1,p);
            }c.restore();
        }
    }
    static final class MapArt extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MapArt(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(getWidth()/100f,getHeight()/100f);
            p.setColor(PALE);p.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,100,100,22,22,p);
            p.setColor(WHITE);p.setStrokeWidth(9);c.drawLine(0,27,100,45,p);c.drawLine(25,0,52,100,p);c.drawLine(0,78,100,61,p);
            p.setColor(TEAL);p.setStrokeWidth(3.5f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            Path path=new Path();path.moveTo(24,66);path.lineTo(47,62);path.lineTo(41,38);path.lineTo(75,43);c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);p.setColor(WHITE);c.drawCircle(24,66,7,p);c.drawCircle(75,43,8,p);
            p.setColor(TEAL);c.drawCircle(24,66,4,p);p.setColor(ORANGE);c.drawCircle(75,43,5,p);c.restore();
        }
    }
    static final class Sheet {
        final Dialog dialog; final LinearLayout body,footer;
        Sheet(Context c,String title){
            dialog=new Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root=column(c);root.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,20));root.setBackground(background(c,WHITE,26,0));
            TextView grab=text(c,"",1,MUTED,false);grab.setBackground(background(c,LINE,4,0));
            LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(c,38),dp(c,4));gl.gravity=Gravity.CENTER;gl.bottomMargin=dp(c,16);root.addView(grab,gl);
            LinearLayout heading=row(c);weight(heading,text(c,title,22,INK,true));
            Button close=button(c,"关闭",false,dialog::dismiss);close.setTextSize(13);add(heading,close,dp(c,64),-2);add(root,heading,-1,-2);gap(root,12);
            ScrollView scroll=new ScrollView(c){@Override protected void onMeasure(int w,int h){
                int cap=getResources().getDisplayMetrics().heightPixels-dp(getContext(),230);
                if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));
                super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.max(dp(getContext(),120),cap),MeasureSpec.AT_MOST));
            }};
            scroll.setClipToPadding(false);body=column(c);scroll.addView(body);add(root,scroll,-1,-2);footer=column(c);gap(root,12);add(root,footer,-1,-2);
            dialog.setContentView(root);Window w=dialog.getWindow();
            if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setDimAmount(.35f);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        }
        void show(){dialog.show();Window w=dialog.getWindow();if(w!=null)w.setLayout(-1,-2);}
        void action(Context c,String label,Runnable r){footer.removeAllViews();add(footer,button(c,label,true,r),-1,-2);}
    }
    private Ui(){}
}
