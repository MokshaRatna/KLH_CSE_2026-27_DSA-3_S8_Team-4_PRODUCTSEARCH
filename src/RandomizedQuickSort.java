import java.util.function.ToDoubleFunction;

/** Randomized QuickSort: a random pivot makes the expected running time O(n log n) on every input. */
public class RandomizedQuickSort {
    private static long seed = 123456789L;
    private static int rand(int n) {
        seed = (seed * 6364136223846793005L + 1442695040888963407L);
        long x = seed ^ (seed >>> 33);
        if (x < 0) x = -x;
        return (int)(x % n);
    }

    // ---- sorting products ---------------------------------------------------------------
    /** Original version: sort by list price. */
    public static void sort(Product[] a, int lo, int hi) { sort(a, lo, hi, p -> p.price); }

    /** Sort products ascending by any numeric key (e.g. cheapest-seller price, rating). */
    public static void sort(Product[] a, int lo, int hi, ToDoubleFunction<Product> key) {
        if (lo >= hi) return;
        int p = partition(a, lo, hi, key);
        sort(a, lo, p - 1, key); sort(a, p + 1, hi, key);
    }
    private static int partition(Product[] a, int lo, int hi, ToDoubleFunction<Product> key) {
        int r = lo + rand(hi - lo + 1);
        Product tmp = a[r]; a[r] = a[hi]; a[hi] = tmp;
        double pivot = key.applyAsDouble(a[hi]);
        int i = lo;
        for (int j = lo; j < hi; j++) if (key.applyAsDouble(a[j]) <= pivot) {
            tmp = a[i]; a[i] = a[j]; a[j] = tmp; i++;
        }
        tmp = a[i]; a[i] = a[hi]; a[hi] = tmp;
        return i;
    }

    /** Server-side sort modes used by the website's sort dropdown. */
    public static void sortProducts(Product[] a, String mode) {
        if ("priceAsc".equals(mode))       sort(a, 0, a.length - 1, p -> OfferStore.min(p.id, p.price));
        else if ("priceDesc".equals(mode)) sort(a, 0, a.length - 1, p -> -OfferStore.min(p.id, p.price));
        else if ("rating".equals(mode))    sort(a, 0, a.length - 1, p -> -p.rating);
    }

    // ---- sorting seller offers (same algorithm, applied to Offer objects) ------------------
    public static void sortOffersByPrice(Offer[] a, int lo, int hi) {
        if (lo >= hi) return;
        int r = lo + rand(hi - lo + 1);
        Offer tmp = a[r]; a[r] = a[hi]; a[hi] = tmp;
        double pivot = a[hi].price;
        int i = lo;
        for (int j = lo; j < hi; j++) if (a[j].price <= pivot) { tmp = a[i]; a[i] = a[j]; a[j] = tmp; i++; }
        tmp = a[i]; a[i] = a[hi]; a[hi] = tmp;
        sortOffersByPrice(a, lo, i - 1); sortOffersByPrice(a, i + 1, hi);
    }
}
