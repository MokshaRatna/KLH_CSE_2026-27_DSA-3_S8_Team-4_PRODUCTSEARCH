# ShopSearch — Product Search, Seller Comparison & Recommendation System (DSA Project)

A file-based, Amazon / Flipkart / Meesho-style shopping website in Java. The search, sorting, comparison and
recommendation logic is hand-written DSA; the browser frontend only presents it. **No SQL database** — everything
is stored in CSV files.

## What the website does
| Feature | Where to see it |
|---|---|
| Search with typo correction | search box (`erbuds` → earbuds) |
| Browse 20 categories, sort, "Load more" | nav bar, sort dropdown |
| **Same product, several sellers, different prices** | product page → *Compare sellers* table (best price / top-rated / fastest delivery tags, "save ₹X" banner) |
| **Seller ratings + product ratings** | cards, product page, cart |
| **Cheaper alternatives + Recommended for you** | bottom of the product page |
| **Top deals** (largest price gap between sellers) | home page |
| **Real product images** (with automatic fallback) | `images/real/` — see below |
| **Wishlist (♥)** | heart on any card → Wishlist page → "Add to cart (best price)" |
| **Cart** (choose seller, change quantity) | Cart page |
| **Checkout + payment** (UPI / Card / Cash on Delivery) | Checkout page — *simulated gateway, no real money* |
| **Order history** | Orders page |
| Register / Login / Logout | top-right button |

## Run (Java 8 or newer)
Windows: double-click `run.bat`   |   Mac/Linux: `./run.sh`   |   or manually:
```
javac -d out src/*.java
java -cp out Main
```
Open **http://localhost:8080**. Stop with Ctrl+C. (Sessions live in memory, so after restarting the server you log in
again — your wishlist, cart and orders are kept because they are in the CSV files.)

Demo payment: card `4111 1111 1111 1111`, any future expiry (e.g. 12/29), any CVV. UPI: any `name@bank`.

## DSA used (and where)
| Topic | File | Used for | Complexity |
|---|---|---|---|
| KMP string matching (Module 2) | `KMP.java`, `Server.runSearch` | every query word must occur in name/brand/category/keywords | O(n+m) per text |
| Edit Distance DP (Module 3) | `EditDistance.java`, `Server.correct` | typo correction against a vocabulary; name similarity in recommendations | O(nm) time, O(m) space |
| Randomized QuickSort (Module 6) | `RandomizedQuickSort.java` | sort sellers by price (once, at load); sort results by cheapest-seller price / rating | expected O(n log n) |
| **Min-heap, Top-K** | `TopK.java`, `RecommendationEngine.java` | recommendations, cheaper alternatives, top deals, search ranking | O(n log K) |
| **HashMap** | `OfferStore.java`, `Store.java` | productId → sellers, user → wishlist/cart, token → session | O(1) average |
| Luhn checksum | `Server.luhn` | card-number validation | O(digits) |

## Data files (no SQL)
| File | Contents |
|---|---|
| `data/products.csv` | 2,000 products |
| `data/sellers.csv` | 5,752 seller offers: productId, seller, sellerRating, price, deliveryDays, stock (regenerate with `python tools/make_sellers.py`) |
| `data/users.csv`, `wishlist.csv`, `cart.csv`, `orders.csv` | created automatically when you use the site |

Passwords are stored as salted SHA-256 hashes. Card numbers and CVVs are **never** stored (only "Card ending 1111").
Prices at checkout are always taken from the server's seller data, never from the browser.

## Real product images
The 2,000 images that came in the zip are drawn illustrations (SVG). To show real photos:
* one product: copy a photo to `images/real/product_0002.jpg` (id = number in the file name), restart the server;
* by product name (only ~128 distinct names): `python tools/apply_name_photos.py --list`, put photos named after the
  products in `photos_by_name/`, then run `python tools/apply_name_photos.py`.
Products without a photo keep their drawn image automatically.

## HTTP API (used by the frontend)
`GET /api/products?category=&page=&sort=` · `GET /api/search?q=&sort=` · `GET /api/product?id=` (offers + recommendations) ·
`GET /api/deals` · `POST /api/register|login|logout` · `GET /api/me` · `GET /api/wishlist` · `POST /api/wishlist/toggle` ·
`GET /api/cart` · `POST /api/cart/add|update` · `POST /api/checkout` · `GET /api/orders`
(logged-in calls send the session token in the `X-Token` header).

## Suggested demo order
1. Home → show **Top deals** → explain the Top-K heap.
2. Search `wireless`, then a typo `erbuds` → KMP, then Edit Distance DP.
3. Sort "Price: Low to High" → Randomized QuickSort on the cheapest-seller price.
4. Open a product → **Compare sellers** (same product, different sellers/prices/ratings) → cheaper alternatives → recommendations.
5. Click ♥ (login/register appears) → Wishlist page → Add to cart.
6. Cart → change quantity/seller → Checkout → pay by card → confirmation → Orders.
7. Show the CSV files in `data/` to prove there is no SQL database.
