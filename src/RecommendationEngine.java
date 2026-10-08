/**
 * Content-based recommendations. Every product is scored against the target product
 * (same category / brand / colour, similar name via Edit Distance, good rating) and a bounded
 * min-heap (TopK) keeps only the best few.
 */
public class RecommendationEngine {

    static double score(Product p, Product target) {
        double s = 0;
        if (p.category.equalsIgnoreCase(target.category)) s += 50;
        if (p.brand.equalsIgnoreCase(target.brand)) s += 12;
        if (p.color.equalsIgnoreCase(target.color)) s += 5;
        s += Math.max(0, 20 - EditDistance.distance(p.name, target.name));
        if (p.rating >= 4.5) s += 8;
        return s;
    }

    /**
     * "You may also like": the most similar products, best first. The catalog repeats product names
     * many times, so we keep a larger heap and then show only one listing per distinct name
     * (and never the same name as the product being viewed) so the list has variety.
     */
    public static Product[] recommend(Product[] products, Product target, int limit) {
        TopK top = new TopK(limit * 12);
        for (Product p : products) {
            if (p.id == target.id) continue;
            top.offer(p, score(p, target));
        }
        Product[] ranked = top.drainBestFirst();
        java.util.ArrayList<Product> out = new java.util.ArrayList<Product>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        seen.add(target.name.toLowerCase());
        for (Product p : ranked) {
            if (out.size() == limit) break;
            if (seen.add(p.name.toLowerCase())) out.add(p);
        }
        for (Product p : ranked) {                       // not enough variety -> fill with the best remaining
            if (out.size() == limit) break;
            if (!out.contains(p)) out.add(p);
        }
        return out.toArray(new Product[0]);
    }

    /** "Cheaper alternatives": similar products in the same category whose best price is lower. */
    public static Product[] cheaperAlternatives(Product[] products, Product target, int limit) {
        double base = OfferStore.min(target.id, target.price);
        TopK top = new TopK(limit);
        for (Product p : products) {
            if (p.id == target.id || !p.category.equalsIgnoreCase(target.category)) continue;
            double m = OfferStore.min(p.id, p.price);
            if (m >= base) continue;
            top.offer(p, score(p, target) + (base - m) / base * 30); // reward bigger savings
        }
        return top.drainBestFirst();
    }

    /** Home-page "Top deals": products where choosing the cheapest seller saves the most. */
    public static Product[] topDeals(Product[] products, int limit) {
        TopK top = new TopK(limit);
        for (Product p : products) top.offer(p, OfferStore.savingsPercent(p.id));
        return top.drainBestFirst();
    }
}
