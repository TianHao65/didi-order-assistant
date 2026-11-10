package local.ridewatch;

import java.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;

/** Local structured data for readable order previews; raw text lives in troubleshooting. */
final class OrderCards {
    static JSONObject encode(OrderEngine.Order o, OrderEngine.Rule r, LocalDate today) {
        JSONObject j=new JSONObject();
        try {
            j.put("from",o.origin).put("to",o.destination).put("time",o.time==null?"":o.time.toString())
                .put("date",o.date==null?"":o.date.toString()).put("fare",o.fare).put("raw",o.raw)
                .put("matched",r.matches(o,today)).put("needsMapping",!o.explicitRoute).put("reason",reason(o,r,today));
        } catch(JSONException ignored) {} return j;
    }
    private static String reason(OrderEngine.Order o,OrderEngine.Rule r,LocalDate today) {
        if(!o.explicitRoute)return "先指定上车和下车地点";
        if(!OrderEngine.containsAny(o.origin,r.origin))return "出发地不符合你的路线";
        if(!OrderEngine.containsAny(o.destination,r.destination))return "目的地不符合你的路线";
        if(o.fare<r.minimumFare)return "低于你设置的金额";
        if(r.day!=0&&(o.date==null||!o.date.equals(r.day==1?today:today.plusDays(1))))return "出发日期不符合";
        if(!r.matches(o,today))return "出发时间不在所选范围";
        return "符合你设置的条件";
    }
    private OrderCards(){}
}
