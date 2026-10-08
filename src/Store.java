import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * File-based persistence for users, wishlists, carts and orders (no SQL database, like the catalog).
 * Everything lives in memory in HashMaps for O(1) access and is re-written to data/*.csv on every change.
 */
public class Store {
    static final String USERS = "data/users.csv", WISH = "data/wishlist.csv",
                        CART = "data/cart.csv", ORDERS = "data/orders.csv";

    public static class OrderItem {
        public int productId; public String seller; public int qty; public double unitPrice;
        OrderItem(int p, String s, int q, double u) { productId = p; seller = s; qty = q; unitPrice = u; }
    }
    public static class Order {
        public String id, user, name, phone, address, method, payRef, status;
        public long time; public double delivery, total;
        public ArrayList<OrderItem> items = new ArrayList<OrderItem>();
    }

    // username -> {salt, hash, displayName}
    private static final Map<String, String[]> users = new HashMap<String, String[]>();
    private static final Map<String, LinkedHashSet<Integer>> wishlist = new HashMap<String, LinkedHashSet<Integer>>();
    // username -> ("productId|seller" -> quantity), insertion ordered
    private static final Map<String, LinkedHashMap<String, Integer>> carts = new HashMap<String, LinkedHashMap<String, Integer>>();
    private static final ArrayList<Order> orders = new ArrayList<Order>();
    private static final Map<String, String> sessions = new HashMap<String, String>(); // token -> username
    private static final SecureRandom rng = new SecureRandom();

    // ------------------------------------------------------------------ loading
    public static synchronized void load() {
        for (String l : lines(USERS)) { String[] p = l.split(",", -1); if (p.length >= 4) users.put(p[0], new String[]{p[1], p[2], p[3]}); }
        for (String l : lines(WISH)) { String[] p = l.split(",", -1); if (p.length >= 2) wishSet(p[0]).add(Integer.parseInt(p[1])); }
        for (String l : lines(CART)) { String[] p = l.split(",", -1); if (p.length >= 4) cartMap(p[0]).put(p[1] + "|" + p[2], Integer.parseInt(p[3])); }
        for (String l : lines(ORDERS)) {
            String[] p = l.split("\\|", -1);
            if (p.length < 11) continue;
            Order o = new Order();
            o.id = p[0]; o.user = p[1]; o.time = Long.parseLong(p[2]); o.name = p[3]; o.phone = p[4]; o.address = p[5];
            o.method = p[6]; o.payRef = p[7]; o.status = p[8]; o.delivery = Double.parseDouble(p[9]);
            double sub = 0;
            for (String it : p[10].split(";")) {
                String[] f = it.split(":");
                if (f.length < 4) continue;
                OrderItem oi = new OrderItem(Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2]), Double.parseDouble(f[3]));
                o.items.add(oi); sub += oi.qty * oi.unitPrice;
            }
            o.total = sub + o.delivery;
            orders.add(o);
        }
    }
    private static List<String> lines(String file) {
        ArrayList<String> out = new ArrayList<String>();
        File f = new File(file);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String l; while ((l = br.readLine()) != null) if (l.trim().length() > 0) out.add(l);
        } catch (IOException e) { e.printStackTrace(); }
        return out;
    }
    private static void write(String file, List<String> rows) {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String r : rows) w.println(r);
        } catch (IOException e) { e.printStackTrace(); }
    }
    private static LinkedHashSet<Integer> wishSet(String u) {
        LinkedHashSet<Integer> s = wishlist.get(u); if (s == null) { s = new LinkedHashSet<Integer>(); wishlist.put(u, s); } return s;
    }
    private static LinkedHashMap<String, Integer> cartMap(String u) {
        LinkedHashMap<String, Integer> m = carts.get(u); if (m == null) { m = new LinkedHashMap<String, Integer>(); carts.put(u, m); } return m;
    }
    /** Free text is stored in delimiter-separated files, so strip the delimiters. */
    public static String clean(String s) { return s == null ? "" : s.replaceAll("[|,\\r\\n]", " ").trim(); }

    // ------------------------------------------------------------------ users + sessions
    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public static synchronized String register(String user, String pass, String display) {
        if (!user.matches("[A-Za-z0-9_.@-]{3,30}")) return "Username must be 3-30 characters: letters, digits, . _ - @";
        if (pass.length() < 6) return "Password must be at least 6 characters";
        if (users.containsKey(user.toLowerCase())) return "That username is already taken";
        byte[] s = new byte[8]; rng.nextBytes(s);
        StringBuilder salt = new StringBuilder(); for (byte x : s) salt.append(String.format("%02x", x));
        String shown = clean(display).length() == 0 ? user : clean(display);
        users.put(user.toLowerCase(), new String[]{salt.toString(), sha256(salt + pass), shown});
        saveUsers();
        return null; // null = success
    }
    /** @return a new session token, or null if the username/password is wrong */
    public static synchronized String login(String user, String pass) {
        String[] u = users.get(user.toLowerCase());
        if (u == null || !u[1].equals(sha256(u[0] + pass))) return null;
        byte[] t = new byte[16]; rng.nextBytes(t);
        StringBuilder tok = new StringBuilder(); for (byte x : t) tok.append(String.format("%02x", x));
        sessions.put(tok.toString(), user.toLowerCase());
        return tok.toString();
    }
    public static synchronized void logout(String token) { sessions.remove(token); }
    public static synchronized String userOf(String token) { return token == null ? null : sessions.get(token); }
    public static synchronized String displayName(String user) { String[] u = users.get(user); return u == null ? user : u[2]; }
    private static void saveUsers() {
        ArrayList<String> r = new ArrayList<String>();
        for (Map.Entry<String, String[]> e : users.entrySet()) r.add(e.getKey() + "," + e.getValue()[0] + "," + e.getValue()[1] + "," + e.getValue()[2]);
        write(USERS, r);
    }

    // ------------------------------------------------------------------ wishlist
    public static synchronized boolean toggleWish(String user, int productId) {
        LinkedHashSet<Integer> s = wishSet(user);
        boolean now = !s.remove(productId);
        if (now) s.add(productId);
        ArrayList<String> r = new ArrayList<String>();
        for (Map.Entry<String, LinkedHashSet<Integer>> e : wishlist.entrySet()) for (int id : e.getValue()) r.add(e.getKey() + "," + id);
        write(WISH, r);
        return now;
    }
    public static synchronized List<Integer> wishIds(String user) { return new ArrayList<Integer>(wishSet(user)); }

    // ------------------------------------------------------------------ cart
    /** @return list of {productId, seller, qty} */
    public static synchronized List<Object[]> cartItems(String user) {
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        for (Map.Entry<String, Integer> e : cartMap(user).entrySet()) {
            int bar = e.getKey().indexOf('|');
            out.add(new Object[]{Integer.parseInt(e.getKey().substring(0, bar)), e.getKey().substring(bar + 1), e.getValue()});
        }
        return out;
    }
    public static synchronized void setCartQty(String user, int productId, String seller, int qty) {
        LinkedHashMap<String, Integer> c = cartMap(user);
        String key = productId + "|" + seller;
        if (qty <= 0) c.remove(key); else c.put(key, qty);
        saveCarts();
    }
    public static synchronized int cartQty(String user, int productId, String seller) {
        Integer q = cartMap(user).get(productId + "|" + seller); return q == null ? 0 : q;
    }
    public static synchronized void clearCart(String user) { cartMap(user).clear(); saveCarts(); }
    public static synchronized int cartCount(String user) { int n = 0; for (int q : cartMap(user).values()) n += q; return n; }
    private static void saveCarts() {
        ArrayList<String> r = new ArrayList<String>();
        for (Map.Entry<String, LinkedHashMap<String, Integer>> e : carts.entrySet())
            for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                int bar = c.getKey().indexOf('|');
                r.add(e.getKey() + "," + c.getKey().substring(0, bar) + "," + c.getKey().substring(bar + 1) + "," + c.getValue());
            }
        write(CART, r);
    }

    // ------------------------------------------------------------------ orders
    public static synchronized Order addOrder(Order o) {
        o.id = "ORD" + (100000 + orders.size() + 1);
        orders.add(o);
        ArrayList<String> r = new ArrayList<String>();
        for (Order x : orders) {
            StringBuilder it = new StringBuilder();
            for (OrderItem i : x.items) { if (it.length() > 0) it.append(';'); it.append(i.productId + ":" + i.seller + ":" + i.qty + ":" + i.unitPrice); }
            r.add(x.id + "|" + x.user + "|" + x.time + "|" + x.name + "|" + x.phone + "|" + x.address + "|" + x.method + "|" + x.payRef + "|" + x.status + "|" + x.delivery + "|" + it);
        }
        write(ORDERS, r);
        return o;
    }
    /** Newest first. */
    public static synchronized List<Order> ordersOf(String user) {
        ArrayList<Order> out = new ArrayList<Order>();
        for (int i = orders.size() - 1; i >= 0; i--) if (orders.get(i).user.equals(user)) out.add(orders.get(i));
        return out;
    }
    public static String newPayRef() { return "TXN" + (100000000L + (long) (rng.nextDouble() * 899999999L)); }
}
