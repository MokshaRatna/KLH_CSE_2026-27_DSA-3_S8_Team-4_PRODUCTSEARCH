import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;

public class Server {
    static Product[] products;
    static final Map<Integer, Product> byId = new HashMap<Integer, Product>();
    static String[] hay;                                   // lower-case "name category brand keywords" per product
    static final ArrayList<String> vocab = new ArrayList<String>(); // known words, used for typo correction
    static final Map<String, String> realImages = new HashMap<String, String>(); // "product_0001" -> "product_0001.jpg"
    static final int PAGE = 24;

    static class ApiException extends RuntimeException {
        int code;
        ApiException(int code, String msg) { super(msg); this.code = code; }
    }

    // =================================================================== start-up
    public static void start(Product[] p) throws Exception {
        products = p;
        hay = new String[p.length];
        TreeSet<String> words = new TreeSet<String>();
        for (int i = 0; i < p.length; i++) {
            byId.put(p[i].id, p[i]);
            hay[i] = (p[i].name + " " + p[i].category + " " + p[i].brand + " " + p[i].keywords).toLowerCase();
            for (String w : hay[i].split("[^a-z0-9]+")) if (w.length() >= 3 && !w.matches("\\d+")) words.add(w);
        }
        vocab.addAll(words);
        scanRealImages();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Server::staticFile);
        server.createContext("/api/", Server::api);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("==============================================");
        System.out.println(" Product Search & Recommendation System");
        System.out.println(" Frontend: http://localhost:8080");
        System.out.println(" Products loaded: " + products.length + " | seller offers: " + countOffers());
        System.out.println(" Real photos found in images/real: " + realImages.size());
        System.out.println(" DSA: KMP + Edit Distance DP + Randomized QuickSort + Heap (Top-K) + HashMap");
        System.out.println("==============================================");
    }
    static int countOffers() { int n = 0; for (Product x : products) n += OfferStore.offers(x.id).length; return n; }

    static void scanRealImages() {
        String[] names = new File("images/real").list();
        if (names == null) return;
        for (String n : names) {
            int dot = n.lastIndexOf('.');
            if (dot < 0) continue;
            String ext = n.substring(dot + 1).toLowerCase();
            if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp"))
                realImages.put(n.substring(0, dot).toLowerCase(), n);
        }
    }
    static String imageUrl(Product p) {
        String base = p.image.substring(0, p.image.lastIndexOf('.')).toLowerCase();
        String real = realImages.get(base);
        return real != null ? "/images/real/" + real : "/images/" + p.image;
    }

    // =================================================================== small helpers
    static void send(HttpExchange e, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(code, b.length);
        e.getResponseBody().write(b); e.close();
    }
    static String dec(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException ex) { return s; }
    }
    /** Query-string and form-body parameters merged into one map. */
    static Map<String, String> params(HttpExchange e) throws IOException {
        Map<String, String> m = new HashMap<String, String>();
        parseInto(m, e.getRequestURI().getRawQuery());
        if (e.getRequestMethod().equals("POST")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            InputStream in = e.getRequestBody();
            while ((n = in.read(buf)) > 0 && bo.size() < 100000) bo.write(buf, 0, n);
            parseInto(m, new String(bo.toByteArray(), StandardCharsets.UTF_8));
        }
        return m;
    }
    static void parseInto(Map<String, String> m, String s) {
        if (s == null || s.length() == 0) return;
        for (String x : s.split("&")) {
            String[] a = x.split("=", 2);
            m.put(dec(a[0]), a.length == 2 ? dec(a[1]) : "");
        }
    }
    static String get(Map<String, String> p, String k) { String v = p.get(k); return v == null ? "" : v.trim(); }
    static int geti(Map<String, String> p, String k, int def) {
        try { return Integer.parseInt(get(p, k)); } catch (Exception x) { return def; }
    }
    static void needPost(HttpExchange e) { if (!e.getRequestMethod().equals("POST")) throw new ApiException(405, "Use POST"); }
    static String needUser(HttpExchange e) {
        String u = Store.userOf(e.getRequestHeaders().getFirst("X-Token"));
        if (u == null) throw new ApiException(401, "Please log in first");
        return u;
    }
    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\\') b.append("\\\\"); else if (c == '"') b.append("\\\"");
            else if (c == '\n' || c == '\r' || c == '\t') b.append(' ');
            else if (c < 0x20) b.append(' '); else b.append(c);
        }
        return b.toString();
    }
    static String s(String v) { return "\"" + esc(v) + "\""; }
    static Product product(int id) {
        Product p = byId.get(id);
        if (p == null) throw new ApiException(404, "Product not found");
        return p;
    }

    // =================================================================== JSON builders
    static String json(Product p) {
        double min = OfferStore.min(p.id, p.price), max = OfferStore.max(p.id, p.price);
        return "{\"id\":" + p.id + ",\"name\":" + s(p.name) + ",\"category\":" + s(p.category) + ",\"brand\":" + s(p.brand)
            + ",\"price\":" + p.price + ",\"minPrice\":" + min + ",\"maxPrice\":" + max + ",\"sellers\":" + OfferStore.offers(p.id).length
            + ",\"rating\":" + p.rating + ",\"reviews\":" + p.reviews + ",\"color\":" + s(p.color)
            + ",\"description\":" + s(p.description) + ",\"discount\":" + p.discount + ",\"image\":" + s(imageUrl(p))
            + ",\"realImage\":" + realImages.containsKey(p.image.substring(0, p.image.lastIndexOf('.')).toLowerCase()) + "}";
    }
    static String jsonList(Product[] list) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.length; i++) { if (i > 0) b.append(","); b.append(json(list[i])); }
        return b.append("]").toString();
    }
    static String offersJson(int productId) {
        Offer[] o = OfferStore.offers(productId);          // already cheapest-first
        int topRated = 0, fastest = 0;
        for (int i = 1; i < o.length; i++) {
            if (o[i].sellerRating > o[topRated].sellerRating) topRated = i;
            if (o[i].deliveryDays < o[fastest].deliveryDays) fastest = i;
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < o.length; i++) {
            if (i > 0) b.append(",");
            b.append("{\"seller\":" + s(o[i].seller) + ",\"sellerRating\":" + o[i].sellerRating + ",\"price\":" + o[i].price
                + ",\"deliveryDays\":" + o[i].deliveryDays + ",\"stock\":" + o[i].stock + ",\"tags\":[");
            ArrayList<String> tags = new ArrayList<String>();
            if (i == 0) tags.add("Best price");
            if (o.length > 1 && i == topRated) tags.add("Top rated seller");
            if (o.length > 1 && i == fastest) tags.add("Fastest delivery");
            for (int t = 0; t < tags.size(); t++) { if (t > 0) b.append(","); b.append(s(tags.get(t))); }
            b.append("]}");
        }
        return b.append("]").toString();
    }

    // =================================================================== router
    static void api(HttpExchange e) throws IOException {
        try {
            String path = e.getRequestURI().getPath();
            Map<String, String> p = params(e);
            String body;
            switch (path) {
                case "/api/products":        body = productsApi(p); break;
                case "/api/search":          body = searchApi(p); break;
                case "/api/product":         body = productApi(p); break;
                case "/api/deals":           body = "{\"products\":" + jsonList(RecommendationEngine.topDeals(products, 12)) + "}"; break;
                case "/api/register":        needPost(e); body = registerApi(p); break;
                case "/api/login":           needPost(e); body = loginApi(p); break;
                case "/api/logout":          needPost(e); Store.logout(e.getRequestHeaders().getFirst("X-Token")); body = "{\"ok\":true}"; break;
                case "/api/me":              body = meApi(e); break;
                case "/api/wishlist":        body = wishlistApi(needUser(e)); break;
                case "/api/wishlist/toggle": needPost(e); body = toggleWishApi(needUser(e), p); break;
                case "/api/cart":            body = cartJson(needUser(e)); break;
                case "/api/cart/add":        needPost(e); body = cartAddApi(needUser(e), p); break;
                case "/api/cart/update":     needPost(e); body = cartUpdateApi(needUser(e), p); break;
                case "/api/checkout":        needPost(e); body = checkoutApi(needUser(e), p); break;
                case "/api/orders":          body = ordersApi(needUser(e)); break;
                default: throw new ApiException(404, "Unknown API");
            }
            send(e, 200, body);
        } catch (ApiException ex) {
            send(e, ex.code, "{\"error\":" + s(ex.getMessage()) + "}");
        } catch (Exception ex) {
            ex.printStackTrace();
            send(e, 500, "{\"error\":\"Server error\"}");
        }
    }

    // =================================================================== catalog
    static String productsApi(Map<String, String> p) {
        String cat = get(p, "category"), sort = get(p, "sort");
        int page = Math.max(1, geti(p, "page", 1));
        ArrayList<Product> f = new ArrayList<Product>();
        for (Product x : products) if (cat.length() == 0 || x.category.equalsIgnoreCase(cat)) f.add(x);
        Product[] arr = f.toArray(new Product[0]);
        RandomizedQuickSort.sortProducts(arr, sort);      // price / rating sorting done by the DSA engine
        int start = Math.min((page - 1) * PAGE, arr.length), end = Math.min(start + PAGE, arr.length);
        return "{\"total\":" + arr.length + ",\"page\":" + page + ",\"products\":" + jsonList(Arrays.copyOfRange(arr, start, end)) + "}";
    }

    static boolean anyContains(String token) {
        for (int i = 0; i < hay.length; i++) if (KMP.contains(hay[i], token)) return true;
        return false;
    }
    /** Nearest known word by Levenshtein distance (only accepted if it is "close enough"). */
    static String correct(String token) {
        String best = token; int bd = 99;
        for (String w : vocab) {
            int d = EditDistance.distance(token, w);
            if (d < bd) { bd = d; best = w; }
        }
        return bd <= Math.max(1, Math.min(3, token.length() / 3)) ? best : token;
    }

    static String searchApi(Map<String, String> p) {
        String term = get(p, "q"), sort = get(p, "sort");
        String[] tokens = term.toLowerCase().split("\\s+");
        if (term.length() == 0) tokens = new String[0];
        String corrected = "";
        boolean partial = false;
        Product[] found = runSearch(tokens, true).drainBestFirst();
        if (found.length == 0 && tokens.length > 0) {     // nothing matched: try fixing typos with Edit Distance
            String[] fixed = new String[tokens.length];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tokens.length; i++) {
                fixed[i] = anyContains(tokens[i]) ? tokens[i] : correct(tokens[i]);
                sb.append(i > 0 ? " " : "").append(fixed[i]);
            }
            found = runSearch(fixed, true).drainBestFirst();
            if (found.length > 0) corrected = sb.toString();
            else { tokens = fixed; }
        }
        if (found.length == 0 && tokens.length > 1) {      // still nothing: show products matching SOME of the words
            found = runSearch(tokens, false).drainBestFirst();
            partial = found.length > 0;
        }
        RandomizedQuickSort.sortProducts(found, sort);
        return "{\"query\":" + s(term) + ",\"corrected\":" + s(corrected) + ",\"partial\":" + partial + ",\"count\":" + found.length + ",\"products\":" + jsonList(found) + "}";
    }
    /**
     * KMP search. requireAll=true: every word of the query must occur in the product text;
     * false: at least one word must. The best 120 by relevance are kept in a heap.
     */
    static TopK runSearch(String[] tokens, boolean requireAll) {
        TopK top = new TopK(120);
        for (int i = 0; i < products.length; i++) {
            Product x = products[i];
            double score = x.rating / 10.0; int hits = 0;
            for (String t : tokens) {
                if (!KMP.contains(hay[i], t)) continue;
                hits++;
                score += KMP.contains(x.name, t) ? 3 : KMP.contains(x.brand, t) ? 2 : 1;
            }
            if (requireAll ? hits == tokens.length : hits > 0) top.offer(x, score);
        }
        return top;
    }

    static String productApi(Map<String, String> p) {
        Product x = product(geti(p, "id", -1));
        double min = OfferStore.min(x.id, x.price), max = OfferStore.max(x.id, x.price);
        return "{\"product\":" + json(x) + ",\"offers\":" + offersJson(x.id) + ",\"maxSaving\":" + (max - min)
            + ",\"recommended\":" + jsonList(RecommendationEngine.recommend(products, x, 8))
            + ",\"cheaper\":" + jsonList(RecommendationEngine.cheaperAlternatives(products, x, 4)) + "}";
    }

    // =================================================================== accounts, wishlist
    static String registerApi(Map<String, String> p) {
        String err = Store.register(get(p, "user"), p.containsKey("pass") ? p.get("pass") : "", get(p, "name"));
        if (err != null) throw new ApiException(400, err);
        return loginApi(p);
    }
    static String loginApi(Map<String, String> p) {
        String tok = Store.login(get(p, "user"), p.containsKey("pass") ? p.get("pass") : "");
        if (tok == null) throw new ApiException(401, "Wrong username or password");
        String u = get(p, "user").toLowerCase();
        return "{\"token\":" + s(tok) + ",\"user\":" + s(u) + ",\"name\":" + s(Store.displayName(u)) + "}";
    }
    static String meApi(HttpExchange e) {
        String u = Store.userOf(e.getRequestHeaders().getFirst("X-Token"));
        if (u == null) return "{\"loggedIn\":false}";
        StringBuilder w = new StringBuilder("[");
        List<Integer> ids = Store.wishIds(u);
        for (int i = 0; i < ids.size(); i++) w.append(i > 0 ? "," : "").append(ids.get(i));
        return "{\"loggedIn\":true,\"user\":" + s(u) + ",\"name\":" + s(Store.displayName(u)) + ",\"wishlist\":" + w + "],\"cartCount\":" + Store.cartCount(u) + "}";
    }
    static String wishlistApi(String u) {
        ArrayList<Product> l = new ArrayList<Product>();
        for (int id : Store.wishIds(u)) if (byId.containsKey(id)) l.add(byId.get(id));
        return "{\"products\":" + jsonList(l.toArray(new Product[0])) + "}";
    }
    static String toggleWishApi(String u, Map<String, String> p) {
        Product x = product(geti(p, "id", -1));
        return "{\"wished\":" + Store.toggleWish(u, x.id) + "}";
    }

    // =================================================================== cart
    static double deliveryFee(double subtotal) { return subtotal == 0 || subtotal >= 500 ? 0 : 40; }

    static String cartJson(String u) {
        StringBuilder b = new StringBuilder("{\"items\":[");
        double sub = 0; int count = 0; boolean first = true;
        for (Object[] it : Store.cartItems(u)) {
            int id = (Integer) it[0]; String seller = (String) it[1]; int qty = (Integer) it[2];
            Product x = byId.get(id); Offer o = OfferStore.find(id, seller);
            if (x == null || o == null) { Store.setCartQty(u, id, seller, 0); continue; }   // listing disappeared
            if (!first) b.append(","); first = false;
            sub += o.price * qty; count += qty;
            b.append("{\"id\":" + id + ",\"name\":" + s(x.name) + ",\"brand\":" + s(x.brand) + ",\"image\":" + s(imageUrl(x))
                + ",\"seller\":" + s(seller) + ",\"sellerRating\":" + o.sellerRating + ",\"price\":" + o.price + ",\"qty\":" + qty
                + ",\"stock\":" + o.stock + ",\"deliveryDays\":" + o.deliveryDays + ",\"subtotal\":" + (o.price * qty) + "}");
        }
        double del = deliveryFee(sub);
        return b.append("],\"count\":" + count + ",\"subtotal\":" + sub + ",\"delivery\":" + del + ",\"total\":" + (sub + del) + "}").toString();
    }
    static String cartAddApi(String u, Map<String, String> p) {
        Product x = product(geti(p, "id", -1));
        String seller = get(p, "seller");
        Offer[] offers = OfferStore.offers(x.id);
        if (offers.length == 0) throw new ApiException(400, "No seller has this product");
        Offer o = seller.length() == 0 ? offers[0] : OfferStore.find(x.id, seller);   // no seller given -> cheapest
        if (o == null) throw new ApiException(400, "Seller not found");
        int qty = Math.max(1, geti(p, "qty", 1));
        int total = Store.cartQty(u, x.id, o.seller) + qty;
        if (total > Math.min(10, o.stock)) throw new ApiException(400, "Only " + Math.min(10, o.stock) + " units available from this seller");
        Store.setCartQty(u, x.id, o.seller, total);
        return cartJson(u);
    }
    static String cartUpdateApi(String u, Map<String, String> p) {
        Product x = product(geti(p, "id", -1));
        String seller = get(p, "seller");
        Offer o = OfferStore.find(x.id, seller);
        if (o == null) throw new ApiException(400, "Seller not found");
        int qty = geti(p, "qty", 1);
        if (qty > Math.min(10, o.stock)) throw new ApiException(400, "Only " + Math.min(10, o.stock) + " units available from this seller");
        Store.setCartQty(u, x.id, seller, qty);          // qty <= 0 removes the item
        return cartJson(u);
    }

    // =================================================================== checkout + payment (simulated)
    static boolean luhn(String num) {
        int sum = 0; boolean dbl = false;
        for (int i = num.length() - 1; i >= 0; i--) {
            int d = num.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d; dbl = !dbl;
        }
        return sum % 10 == 0;
    }
    static String checkoutApi(String u, Map<String, String> p) {
        List<Object[]> items = Store.cartItems(u);
        if (items.isEmpty()) throw new ApiException(400, "Your cart is empty");

        String name = Store.clean(get(p, "name")), phone = get(p, "phone").replaceAll("\\s", "");
        String addr = Store.clean(get(p, "address")), pin = get(p, "pincode");
        if (name.length() < 2) throw new ApiException(400, "Enter the receiver's name");
        if (!phone.matches("[6-9]\\d{9}")) throw new ApiException(400, "Enter a valid 10-digit mobile number");
        if (addr.length() < 10) throw new ApiException(400, "Enter a full delivery address");
        if (!pin.matches("\\d{6}")) throw new ApiException(400, "Enter a 6-digit PIN code");

        String method = get(p, "method"), label, status, ref;
        if (method.equals("upi")) {
            if (!get(p, "upi").matches("[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}")) throw new ApiException(400, "Enter a valid UPI ID like name@bank");
            label = "UPI"; status = "Paid - confirmed"; ref = Store.newPayRef();
        } else if (method.equals("card")) {
            String num = get(p, "cardNumber").replaceAll("[\\s-]", "");
            if (!num.matches("\\d{13,19}") || !luhn(num)) throw new ApiException(400, "Card number is not valid");
            if (get(p, "cardName").length() < 2) throw new ApiException(400, "Enter the name on the card");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2})\\s*/\\s*(\\d{2})").matcher(get(p, "expiry"));
            if (!m.matches()) throw new ApiException(400, "Expiry must be MM/YY");
            int mm = Integer.parseInt(m.group(1)), yy = 2000 + Integer.parseInt(m.group(2));
            Calendar now = Calendar.getInstance();
            if (mm < 1 || mm > 12 || yy < now.get(Calendar.YEAR) || (yy == now.get(Calendar.YEAR) && mm < now.get(Calendar.MONTH) + 1))
                throw new ApiException(400, "This card has expired");
            if (!get(p, "cvv").matches("\\d{3,4}")) throw new ApiException(400, "Enter the 3-digit CVV");
            // Only the last 4 digits are kept. The full number and CVV are never stored.
            label = "Card ending " + num.substring(num.length() - 4); status = "Paid - confirmed"; ref = Store.newPayRef();
        } else if (method.equals("cod")) {
            label = "Cash on Delivery"; status = "Placed - pay on delivery"; ref = "PAY-ON-DELIVERY";
        } else throw new ApiException(400, "Choose a payment method");

        // Prices always come from the server's seller data, never from the browser.
        Store.Order o = new Store.Order();
        double sub = 0;
        for (Object[] it : items) {
            int id = (Integer) it[0]; String seller = (String) it[1]; int qty = (Integer) it[2];
            Offer off = OfferStore.find(id, seller);
            if (off == null || qty > off.stock) throw new ApiException(400, "An item in your cart is no longer available - please review your cart");
            o.items.add(new Store.OrderItem(id, seller, qty, off.price)); sub += off.price * qty;
        }
        o.user = u; o.name = name; o.phone = phone; o.address = addr + " - " + pin; o.method = label; o.payRef = ref;
        o.status = status; o.time = System.currentTimeMillis(); o.delivery = deliveryFee(sub); o.total = sub + o.delivery;
        o = Store.addOrder(o);
        Store.clearCart(u);
        return "{\"order\":" + orderJson(o) + "}";
    }

    static String orderJson(Store.Order o) {
        StringBuilder b = new StringBuilder("{\"id\":" + s(o.id) + ",\"time\":" + o.time + ",\"status\":" + s(o.status) + ",\"method\":" + s(o.method)
            + ",\"payRef\":" + s(o.payRef) + ",\"name\":" + s(o.name) + ",\"phone\":" + s(o.phone) + ",\"address\":" + s(o.address)
            + ",\"delivery\":" + o.delivery + ",\"total\":" + o.total + ",\"items\":[");
        for (int i = 0; i < o.items.size(); i++) {
            Store.OrderItem it = o.items.get(i); Product x = byId.get(it.productId);
            if (i > 0) b.append(",");
            b.append("{\"id\":" + it.productId + ",\"name\":" + s(x == null ? "Product #" + it.productId : x.name) + ",\"image\":"
                + s(x == null ? "" : imageUrl(x)) + ",\"seller\":" + s(it.seller) + ",\"qty\":" + it.qty + ",\"price\":" + it.unitPrice + "}");
        }
        return b.append("]}").toString();
    }
    static String ordersApi(String u) {
        StringBuilder b = new StringBuilder("{\"orders\":[");
        List<Store.Order> l = Store.ordersOf(u);
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(","); b.append(orderJson(l.get(i))); }
        return b.append("]}").toString();
    }

    // =================================================================== static files
    static void staticFile(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        File base = new File(path.startsWith("/images/") ? "images" : "frontend").getCanonicalFile();
        File f = new File(base, path.startsWith("/images/") ? path.substring("/images/".length()) : path.substring(1)).getCanonicalFile();
        // refuse "../" tricks: the file must really be inside the folder we serve
        if (!f.getPath().startsWith(base.getPath() + File.separator) || !f.isFile()) { e.sendResponseHeaders(404, -1); e.close(); return; }
        String n = f.getName().toLowerCase(), type;
        if (n.endsWith(".html")) type = "text/html; charset=UTF-8";
        else if (n.endsWith(".css")) type = "text/css; charset=UTF-8";
        else if (n.endsWith(".js")) type = "application/javascript; charset=UTF-8";
        else if (n.endsWith(".svg")) type = "image/svg+xml";
        else if (n.endsWith(".png")) type = "image/png";
        else if (n.endsWith(".webp")) type = "image/webp";
        else if (n.endsWith(".jpg") || n.endsWith(".jpeg")) type = "image/jpeg";
        else type = "application/octet-stream";
        byte[] data = Files.readAllBytes(f.toPath());
        e.getResponseHeaders().set("Content-Type", type);
        if (path.startsWith("/images/")) e.getResponseHeaders().set("Cache-Control", "max-age=3600");
        e.sendResponseHeaders(200, data.length);
        e.getResponseBody().write(data); e.close();
    }
}
