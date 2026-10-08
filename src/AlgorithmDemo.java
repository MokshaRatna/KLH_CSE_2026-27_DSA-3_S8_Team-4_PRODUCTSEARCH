public class AlgorithmDemo {
    public static void main(String[] args) throws Exception {
        Product[] p = ProductDataLoader.load("data/products.csv");
        OfferStore.load("data/sellers.csv");
        System.out.println("\n=== DSA DEMONSTRATION ===");
        System.out.println("Products loaded: " + p.length);

        String text="wireless headphones with bluetooth";
        String pattern="headphones";
        System.out.println("\nKMP: searching '" + pattern + "'");
        System.out.println("Match: " + KMP.contains(text, pattern));
        System.out.println("Complexity: O(n + m)");

        System.out.println("\nLevenshtein DP:");
        System.out.println("Distance between 'hedphones' and 'headphones' = " +
            EditDistance.distance("hedphones","headphones"));
        System.out.println("Complexity: O(n*m), space O(m)");

        Product[] sample=new Product[10];
        for(int i=0;i<10;i++) sample[i]=p[i];
        System.out.println("\nRandomized QuickSort by cheapest-seller price:");
        RandomizedQuickSort.sortProducts(sample, "priceAsc");
        for(Product x:sample) System.out.println(x.name+" -> from Rs."+OfferStore.min(x.id, x.price));
        System.out.println("Expected complexity: O(n log n)");

        Product t = p[1];
        System.out.println("\nSeller offers for '" + t.name + "' (HashMap lookup O(1), sorted cheapest first):");
        for(Offer o : OfferStore.offers(t.id))
            System.out.println("  " + o.seller + "  rating " + o.sellerRating + "  Rs." + o.price + "  delivery " + o.deliveryDays + "d");

        System.out.println("\nTop-K min-heap -> 5 most similar products to '" + t.name + "':");
        for(Product r : RecommendationEngine.recommend(p, t, 5)) System.out.println("  " + r.name + " (" + r.category + ")");
        System.out.println("Complexity: O(n log K) for n products, K results");

        System.out.println("\nTop-K min-heap -> 3 biggest seller price gaps:");
        for(Product d : RecommendationEngine.topDeals(p, 3))
            System.out.printf("  %s: Rs.%.0f - Rs.%.0f (%.1f%% saving)%n", d.name, OfferStore.min(d.id,d.price), OfferStore.max(d.id,d.price), OfferStore.savingsPercent(d.id));
    }
}
