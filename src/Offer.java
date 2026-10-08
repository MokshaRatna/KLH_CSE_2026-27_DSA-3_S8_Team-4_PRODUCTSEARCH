/** One seller's listing of a product: the same product can be sold by several sellers at different prices. */
public class Offer {
    public int productId;
    public String seller;
    public double sellerRating;
    public double price;
    public int deliveryDays;
    public int stock;

    public Offer(int productId, String seller, double sellerRating, double price, int deliveryDays, int stock) {
        this.productId = productId; this.seller = seller; this.sellerRating = sellerRating;
        this.price = price; this.deliveryDays = deliveryDays; this.stock = stock;
    }
}
