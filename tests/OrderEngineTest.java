package local.ridewatch;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public final class OrderEngineTest {
    private static int checks;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static OrderEngine.Order card(String... lines) { return OrderEngine.parseCard(Arrays.asList(lines), TODAY); }
    private static OrderEngine.Node node(String... lines) {
        OrderEngine.Node n = new OrderEngine.Node("", false);
        for (String s : lines) n.add(new OrderEngine.Node(s, false)); return n;
    }
    public static void main(String[] args) {
        OrderEngine.Order order = card("出发地：杭州西湖", "目的地：上海虹桥", "今天 09:30 出发", "128.50元");
        check(order != null && order.origin.equals("杭州西湖") && order.destination.equals("上海虹桥"), "labelled route");
        check(order.fare == 128.5 && order.time.equals(LocalTime.of(9,30)), "fare and time");
        check(order.date.equals(TODAY), "today");
        OrderEngine.Rule rule = new OrderEngine.Rule(); rule.origin = "余杭，西湖"; rule.destination = "上海";
        rule.minimumFare = 100; rule.start = LocalTime.of(8,0); rule.end = LocalTime.of(10,0); rule.day = 1;
        check(rule.matches(order, TODAY), "combined filter and keyword alternatives");
        rule.destination = "嘉兴"; check(!rule.matches(order, TODAY), "wrong destination"); rule.destination = "上海";
        rule.origin = "上海"; rule.destination = "杭州"; check(!rule.matches(order, TODAY), "reverse direction rejected");
        rule.origin = "杭州"; rule.destination = "上海"; rule.minimumFare = 129; check(!rule.matches(order, TODAY), "minimum fare"); rule.minimumFare = 0;
        rule.start = LocalTime.of(10,0); rule.end = LocalTime.of(12,0); check(!rule.matches(order, TODAY), "out of time range");
        OrderEngine.Order late = card("起点：杭州", "终点：上海", "明天 23：30", "￥90");
        rule.start = LocalTime.of(22,0); rule.end = LocalTime.of(2,0); rule.day = 2;
        check(rule.matches(late, TODAY), "cross-midnight late");
        check(rule.matches(card("起点：杭州", "终点：上海", "明天 01:30", "90元"), TODAY), "cross-midnight early");
        rule.day = 1; check(!rule.matches(late, TODAY), "wrong date");
        rule.start = null; rule.end = null; rule.day = 0;
        OrderEngine.Order unknown = card("杭州 → 上海", "09:30", "¥90");
        check(unknown != null && rule.matches(unknown, TODAY), "explicit arrow and unknown date when unrestricted");
        rule.day = 1; check(!rule.matches(unknown, TODAY), "unknown date rejected for today filter");
        check(card("杭州", "上海", "09:30", "90元") == null, "unlabelled route not guessed");
        check(card("起点：杭州", "终点：上海", "09:30", "90元", "100元") == null, "ambiguous prices");
        check(card("起点：杭州", "终点：上海", "09:30–10:30", "90元") == null, "ambiguous time interval");
        check(card("起点：杭州", "终点：上海", "25:30", "90元") == null, "invalid time");
        check(card("起点：杭州", "终点：上海", "11月31日 09:30", "90元") == null, "invalid date");
        check(card("起点：杭州", "终点：上海", "02月29日 09:30", "90元") == null, "invalid leap day is not shifted");
        check(card("起点：杭州", "终点：上海", "09:30", "90.123元") == null, "invalid monetary decimal is not parsed as a suffix");
        check(card("起点", "杭州", "终点", "上海", "后天 09:30", "90元").date.equals(TODAY.plusDays(2)), "split labels and day after tomorrow");
        check(card("起点：杭州", "终点：上海", "2026-11-11 09:30", "90元").date.equals(TODAY.plusDays(1)), "full date");
        check(card("起点：杭州", "终点：上海", "01月02日 09:30", "90元").date.equals(LocalDate.of(2027,1,2)), "year rollover");
        OrderEngine.Node list = new OrderEngine.Node("顺风车订单", true);
        list.add(node("出发地：杭州", "目的地：上海", "今天 09:30", "128元"));
        list.add(node("出发地：嘉兴", "目的地：苏州", "今天 10:30", "68元"));
        OrderEngine.Node root = new OrderEngine.Node("", false).add(list);
        List<OrderEngine.Order> parsed = OrderEngine.parse(root, TODAY);
        check(parsed.size() == 2, "separate cards rather than whole-screen blending");
        rule.origin = "杭州"; rule.destination = "苏州"; rule.day = 0;
        check(!rule.matches(parsed.get(0), TODAY) && !rule.matches(parsed.get(1), TODAY), "different cards cannot satisfy one route");
        OrderEngine.Node accessibilityCard = new OrderEngine.Node("出发地：杭州\n目的地：上海\n今天 09:30\n128元", false);
        accessibilityCard.add(node("出发地：杭州", "目的地：上海", "今天 09:30", "128元"));
        check(OrderEngine.parse(accessibilityCard, TODAY).size() == 1, "accessibility duplicate content deduplicated");
        check(order.key().equals(card("出发地：杭州西湖", "目的地：上海虹桥", "今天 09:30 出发", "128.50元", "剩余座位 2").key()), "stable reminder key");
        OrderEngine.Node unlabeled = new OrderEngine.Node("", true).add(node("杭州西湖", "上海虹桥", "今天 09:30", "128元"));
        List<OrderEngine.Order> candidates = OrderEngine.parse(unlabeled, TODAY, 0, 0, true);
        rule.origin = ""; rule.destination = "";
        check(candidates.size() == 1 && !rule.matches(candidates.get(0), TODAY), "uncalibrated candidates never trigger reminders");
        List<OrderEngine.Order> calibrated = OrderEngine.parse(unlabeled, TODAY, 1, 2, false);
        rule.origin = "杭州"; rule.destination = "上海";
        check(calibrated.size() == 1 && rule.matches(calibrated.get(0), TODAY), "manual address-line calibration");
        check(OrderEngine.parse(unlabeled, TODAY, 3, 4, false).isEmpty(), "time and fare cannot be address lines");
        System.out.println("PASS: " + checks + " parser and matching checks");
    }
}
