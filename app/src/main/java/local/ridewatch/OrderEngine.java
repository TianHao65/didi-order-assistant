package local.ridewatch;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parser: one candidate must contain one fare, a departure time and a route. */
public final class OrderEngine {
    private static final Pattern MONEY = Pattern.compile("(?:[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)(?![\\d.])|(?<![\\d.])(\\d+(?:\\.\\d{1,2})?)\\s*元)");
    private static final Pattern TIME = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?!\\d)");
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})[-/年])?(\\d{1,2})[-/月](\\d{1,2})日?");
    private static final Pattern ORIGIN = Pattern.compile("^(?:出发地|起点|上车地点|上车点|从)[：:\\s]*(.+)$");
    private static final Pattern DEST = Pattern.compile("^(?:目的地|终点|下车地点|下车点|到)[：:\\s]*(.+)$");

    public static final class Node {
        public final String text;
        public final List<Node> children = new ArrayList<>();
        public final boolean list;
        public Node(String text, boolean list) { this.text = text == null ? "" : text.trim(); this.list = list; }
        public Node add(Node child) { children.add(child); return this; }
        public List<String> lines() {
            LinkedHashSet<String> out = new LinkedHashSet<>(); collect(out); return new ArrayList<>(out);
        }
        private void collect(LinkedHashSet<String> out) {
            for (String s : text.split("[\\n\\r]+")) if (!s.trim().isEmpty()) out.add(s.trim());
            for (Node c : children) c.collect(out);
        }
    }
    public static final class Order {
        public final String raw, origin, destination;
        public final double fare;
        public final LocalTime time;
        public final LocalDate date;
        public final boolean explicitRoute;
        Order(String raw, String origin, String destination, double fare, LocalTime time, LocalDate date) {
            this.raw = raw; this.origin = origin; this.destination = destination; this.fare = fare;
            this.time = time; this.date = date; this.explicitRoute = !origin.isEmpty() && !destination.isEmpty();
        }
        public String key() {
            // Raw card text may contain changing distance/availability counters. Use stable trip attributes.
            String route = explicitRoute ? origin + "→" + destination : raw;
            return route + "|" + fare + "|" + time + "|" + date;
        }
        public String summary() {
            return (explicitRoute ? origin + " → " + destination : "路线方向待核对") + "\n"
                + (date == null ? "日期未识别" : date.toString()) + " " + time + " · ¥" + String.format(Locale.CHINA, "%.2f", fare);
        }
    }
    public static final class Rule {
        public String origin = "", destination = "";
        public double minimumFare;
        public LocalTime start, end;
        /** 0=any date; 1=today; 2=tomorrow. */
        public int day;
        public boolean matches(Order o, LocalDate today) {
            if (o.fare < minimumFare) return false;
            if (o.explicitRoute) {
                if (!containsAny(o.origin, origin) || !containsAny(o.destination, destination)) return false;
            } else return false; // Candidate previews without a calibrated direction never match.
            if (day != 0 && (o.date == null || !o.date.equals(day == 1 ? today : today.plusDays(1)))) return false;
            if (start != null && end != null) {
                if (o.time == null) return false;
                boolean in = start.compareTo(end) <= 0
                    ? !o.time.isBefore(start) && !o.time.isAfter(end)
                    : !o.time.isBefore(start) || !o.time.isAfter(end);
                if (!in) return false;
            }
            return true;
        }
    }
    public static boolean containsAny(String text, String keywords) {
        if (keywords.trim().isEmpty()) return true;
        for (String word : keywords.split("[,，]")) if (!word.trim().isEmpty() && text.contains(word.trim())) return true;
        return false;
    }
    public static List<Order> parse(Node root, LocalDate today) {
        return parse(root, today, 0, 0, false);
    }
    public static List<Order> parse(Node root, LocalDate today, int originLine, int destinationLine, boolean preview) {
        List<Order> out = new ArrayList<>(); scan(root, today, originLine, destinationLine, preview, out); return out;
    }
    private static void scan(Node node, LocalDate today, int originLine, int destinationLine, boolean preview, List<Order> out) {
        int before = out.size();
        for (Node c : node.children) scan(c, today, originLine, destinationLine, preview, out);
        if (out.size() != before || node.list) return;
        Order order = parseCard(node.lines(), today, originLine, destinationLine, preview);
        if (order != null) out.add(order);
    }
    public static Order parseCard(List<String> lines, LocalDate today) {
        return parseCard(lines, today, 0, 0, false);
    }
    private static Order parseCard(List<String> lines, LocalDate today, int originLine, int destinationLine, boolean preview) {
        String raw = String.join("\n", lines);
        if (raw.length() > 900 || lines.size() > 35) return null;
        Matcher money = MONEY.matcher(raw);
        if (!money.find()) return null;
        double fare = Double.parseDouble(money.group(1) != null ? money.group(1) : money.group(2));
        if (money.find() || fare <= 0) return null; // Multiple prices could be multiple orders.
        Matcher t = TIME.matcher(raw);
        if (!t.find()) return null;
        LocalTime time = LocalTime.of(Integer.parseInt(t.group(1)), Integer.parseInt(t.group(2)));
        // Multiple departure times require a page-specific adapter; don't guess.
        if (t.find()) return null;
        String origin = "", destination = "";
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            Matcher a = ORIGIN.matcher(s), b = DEST.matcher(s);
            if (a.matches()) origin = a.group(1).trim();
            if (b.matches()) destination = b.group(1).trim();
            if (s.matches("(?:出发地|起点|上车地点|上车点)[：:]?") && i + 1 < lines.size()) origin = lines.get(i + 1);
            if (s.matches("(?:目的地|终点|下车地点|下车点)[：:]?") && i + 1 < lines.size()) destination = lines.get(i + 1);
            // A textual arrow is an explicit route direction, unlike screen position.
            String[] route = s.split("\\s*(?:→|->)\\s*");
            if (route.length == 2 && !route[0].isEmpty() && !route[1].isEmpty()) { origin = route[0]; destination = route[1]; }
        }
        if (origin.isEmpty() || destination.isEmpty()) {
            if (originLine > 0 && destinationLine > 0 && originLine != destinationLine && lines.size() >= Math.max(originLine, destinationLine)) {
                String a = lines.get(originLine - 1), b = lines.get(destinationLine - 1);
                if (!routeLike(a) || !routeLike(b)) return null;
                origin = a; destination = b;
            } else {
                if (!preview || lines.stream().filter(OrderEngine::routeLike).count() < 2) return null;
                origin = ""; destination = "";
            }
        }
        LocalDate date = null;
        if (raw.contains("明天")) date = today.plusDays(1);
        else if (raw.contains("后天")) date = today.plusDays(2);
        else if (raw.contains("今天") || raw.contains("今日")) date = today;
        else {
            Matcher d = DATE.matcher(raw);
            if (d.find()) try {
                int month = Integer.parseInt(d.group(2)), day = Integer.parseInt(d.group(3));
                if (d.group(1) != null) date = LocalDate.of(Integer.parseInt(d.group(1)), month, day);
                else {
                    date = LocalDate.of(today.getYear(), month, day);
                    if (date.isBefore(today.minusDays(1))) date = date.plusYears(1);
                }
            } catch (RuntimeException ignored) { return null; }
        }
        return new Order(raw, origin, destination, fare, time, date);
    }
    private static boolean routeLike(String line) {
        if (line.length() < 2 || MONEY.matcher(line).find() || TIME.matcher(line).find()) return false;
        if (line.matches(".*(?:公里|km|分钟|座位|接单|抢单|刷新|认证|收入|订单|乘客|司机|顺风车|今天|明天|后天|出发时间|可接|拼车|独享|推荐).*")) return false;
        if (line.matches("[\\d\\s.,人位座]+")) return false;
        return line.matches(".*[\\p{L}].*");
    }
    private OrderEngine() {}
}
