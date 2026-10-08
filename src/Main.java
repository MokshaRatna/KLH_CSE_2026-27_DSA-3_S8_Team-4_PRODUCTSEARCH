public class Main {
    public static void main(String[] args) throws Exception {
        Product[] products = ProductDataLoader.load("data/products.csv");
        OfferStore.load("data/sellers.csv");   // seller offers (price/rating/delivery per seller)
        Store.load();                           // users, wishlists, carts, orders (data/*.csv)
        Server.start(products);
        System.out.println("Open the URL above in Chrome/Edge.");
        System.out.println("Press Ctrl+C to stop.");
    }
}
