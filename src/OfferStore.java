import java.io.*;
import java.util.*;

/**
 * Loads data/sellers.csv and keeps every product's offers in a HashMap (productId -> offers) so a
 * product's sellers are found in O(1). Each product's offers are sorted by price ONCE at load time
 * using Randomized QuickSort, so the cheapest seller is always offers[0].
 */
public class OfferStore {
    private static final Map<Integer, Offer[]> byProduct = new HashMap<Integer, Offer[]>();

    public static void load(String file) throws IOException {
        Map<Integer, ArrayList<Offer>> tmp = new HashMap<Integer, ArrayList<Offer>>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine(); // header
        String line;
        while ((line = br.readLine()) != null) {
            String[] p = line.trim().split(",");
            if (p.length < 6) continue;
            Offer o = new Offer(Integer.parseInt(p[0]), p[1], Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
            ArrayList<Offer> l = tmp.get(o.productId);
            if (l == null) { l = new ArrayList<Offer>(); tmp.put(o.productId, l); }
            l.add(o);
        }
        br.close();
        for (Map.Entry<Integer, ArrayList<Offer>> en : tmp.entrySet()) {
            Offer[] arr = en.getValue().toArray(new Offer[0]);
            RandomizedQuickSort.sortOffersByPrice(arr, 0, arr.length - 1);
            byProduct.put(en.getKey(), arr);
        }
    }

    /** Offers of a product, cheapest first (empty array if none). */
    public static Offer[] offers(int productId) {
        Offer[] o = byProduct.get(productId);
        return o == null ? new Offer[0] : o;
    }
    public static double min(int productId, double fallback) {
        Offer[] o = offers(productId); return o.length == 0 ? fallback : o[0].price;
    }
    public static double max(int productId, double fallback) {
        Offer[] o = offers(productId); return o.length == 0 ? fallback : o[o.length - 1].price;
    }
    public static Offer find(int productId, String seller) {
        for (Offer o : offers(productId)) if (o.seller.equals(seller)) return o;
        return null;
    }
    /** Percentage gap between the cheapest and the most expensive seller (0..100). */
    public static double savingsPercent(int productId) {
        Offer[] o = offers(productId);
        if (o.length < 2) return 0;
        return (o[o.length - 1].price - o[0].price) * 100.0 / o[o.length - 1].price;
    }
}
