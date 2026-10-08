"""Generates data/sellers.csv: 2-4 seller offers for every product in data/products.csv.
Run from the project root:  python tools/make_sellers.py
(Deterministic: same output every run, because the random seed is fixed.)"""
import csv, random

random.seed(2026)
SELLERS = [("TrendyHub Retail", 4.6), ("BharatMart", 4.3), ("UrbanKart Traders", 4.1),
           ("ShopEasy India", 4.4), ("PrimeDeals Store", 4.7), ("Value Bazaar", 3.9),
           ("SunriseTraders", 4.2), ("NovaMart", 4.5), ("KiranaOnline", 4.0),
           ("MegaSave Retail", 3.8), ("Elite Outlet", 4.8), ("QuickCart Seller", 4.2)]

rows = []
with open("data/products.csv", newline="", encoding="utf-8") as f:
    for p in csv.DictReader(f):
        base = float(p["price"])
        n = random.choices([2, 3, 4], weights=[35, 40, 25])[0]
        chosen = random.sample(SELLERS, n)
        offers = []
        for name, r in chosen:
            rating = round(min(5.0, max(3.5, r + random.uniform(-0.15, 0.15))), 1)
            factor = 1.0 + random.uniform(-0.08, 0.08) + (rating - 4.2) * 0.02
            price = max(1, round(base * factor))
            days = max(1, min(7, round(random.uniform(1, 6) - (rating - 4.2) * 1.5)))
            offers.append([p["id"], name, rating, price, days, random.randint(5, 120)])
        prices = [o[3] for o in offers]
        if max(prices) < min(prices) * 1.03:          # make sure the price gap is visible
            hi = max(offers, key=lambda o: o[3]); hi[3] = round(min(prices) * 1.06)
        rows += offers

with open("data/sellers.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f); w.writerow(["productId", "seller", "sellerRating", "price", "deliveryDays", "stock"]); w.writerows(rows)
print(len(rows), "offers written")
