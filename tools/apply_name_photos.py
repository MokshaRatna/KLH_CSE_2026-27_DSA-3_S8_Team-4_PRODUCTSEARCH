"""Use ONE real photo for every listing that shares a product name.

The catalog has ~128 distinct product names (e.g. "Nothing Budget Smartphone Max") repeated across
2,000 listings, so you only need ~128 photos instead of 2,000.

  1) python tools/apply_name_photos.py --list
        -> writes tools/product_names.txt (the 128 names you need photos for)
  2) Put your photos in the folder  photos_by_name/  named after the product, e.g.
        photos_by_name/Nothing Budget Smartphone Max.jpg      (.jpg .jpeg .png .webp)
  3) python tools/apply_name_photos.py
        -> copies each photo to images/real/product_0002.jpg, product_0122.jpg, ... for every matching listing.
Restart the Java server; the website now shows the real photos (listings without one keep the drawn image).
Single-listing override: just drop images/real/product_0002.jpg yourself.
"""
import csv, os, re, shutil, sys

def norm(s): return re.sub(r"[^a-z0-9]", "", s.lower())
rows = list(csv.DictReader(open("data/products.csv", newline="", encoding="utf-8")))

if "--list" in sys.argv:
    names = sorted({r["name"] for r in rows})
    open("tools/product_names.txt", "w", encoding="utf-8").write("\n".join(names) + "\n")
    print(len(names), "names written to tools/product_names.txt"); sys.exit()

src = "photos_by_name"
if not os.path.isdir(src): sys.exit("Create the folder 'photos_by_name' and put your photos in it first.")
photos = {}
for f in os.listdir(src):
    base, ext = os.path.splitext(f)
    if ext.lower() in (".jpg", ".jpeg", ".png", ".webp"): photos[norm(base)] = f
os.makedirs("images/real", exist_ok=True)
copied = used = 0
for r in rows:
    f = photos.get(norm(r["name"]))
    if not f: continue
    ext = os.path.splitext(f)[1].lower()
    shutil.copyfile(os.path.join(src, f), os.path.join("images/real", os.path.splitext(r["image"])[0] + ext)); copied += 1
used = len({norm(r["name"]) for r in rows if norm(r["name"]) in photos})
print(f"{used} photos matched -> {copied} product listings now have a real image")
missing = sorted({r["name"] for r in rows if norm(r["name"]) not in photos})
print(f"{len(missing)} product names still use the drawn image (run with --list to see all names)")
