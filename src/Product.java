
public class Product {
    public int id;
    public String name, category, brand, color, description, keywords, image;
    public double price, rating;
    public int reviews, discount;

    public Product(int id, String name, String category, String brand, double price,
                   double rating, int reviews, String color, String description,
                   String keywords, int discount, String image) {
        this.id=id; this.name=name; this.category=category; this.brand=brand;
        this.price=price; this.rating=rating; this.reviews=reviews; this.color=color;
        this.description=description; this.keywords=keywords; this.discount=discount;
        this.image=image;
    }
}
