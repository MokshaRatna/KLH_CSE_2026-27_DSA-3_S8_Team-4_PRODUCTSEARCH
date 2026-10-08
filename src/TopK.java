/**
 * Bounded MIN-HEAP that keeps only the K highest-scoring products seen so far.
 * Adding one item costs O(log K), so scanning n products costs O(n log K) -- much cheaper than
 * sorting all n products (O(n log n)) when we only need the best few (recommendations, top deals).
 */
public class TopK {
    private final int k;
    private final Product[] items;
    private final double[] keys;
    private int size = 0;

    public TopK(int k) { this.k = Math.max(1, k); items = new Product[this.k]; keys = new double[this.k]; }

    public void offer(Product p, double key) {
        if (size < k) { items[size] = p; keys[size] = key; siftUp(size); size++; }
        else if (key > keys[0]) { items[0] = p; keys[0] = key; siftDown(0); } // beat the weakest of the top K
    }

    /** Returns the kept products best-first. Empties the heap. */
    public Product[] drainBestFirst() {
        Product[] out = new Product[size];
        for (int i = size - 1; i >= 0; i--) {        // repeatedly remove the minimum -> fill from the back
            out[i] = items[0];
            size--; items[0] = items[size]; keys[0] = keys[size];
            if (size > 0) siftDown(0);
        }
        return out;
    }

    private void siftUp(int i) {
        while (i > 0) { int p = (i - 1) / 2; if (keys[i] >= keys[p]) break; swap(i, p); i = p; }
    }
    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < size && keys[l] < keys[m]) m = l;
            if (r < size && keys[r] < keys[m]) m = r;
            if (m == i) return;
            swap(i, m); i = m;
        }
    }
    private void swap(int a, int b) {
        Product tp = items[a]; items[a] = items[b]; items[b] = tp;
        double tk = keys[a]; keys[a] = keys[b]; keys[b] = tk;
    }
}
