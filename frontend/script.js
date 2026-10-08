/* ShopSearch frontend. All data comes from the Java server (/api/...). */
const $ = id => document.getElementById(id);
const enc = encodeURIComponent;
const money = n => "₹" + Number(n).toLocaleString("en-IN", { maximumFractionDigits: 0 });
const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const S = {                       // app state
  token: localStorage.getItem("shop_token") || "", user: "", name: "", wish: new Set(), cartCount: 0,
  mode: "all", cat: "", q: "", page: 1, total: 0, list: [], sort: "default", after: null, authMode: "login"
};
const CATS = ["Electronics","Mobiles","Computers & Accessories","Men's Fashion","Women's Fashion","Footwear","Beauty & Personal Care","Home & Kitchen","Home Appliances","Furniture","Sports & Fitness","Books","Toys & Games","Automotive","Grocery","Health & Wellness","Pet Supplies","Garden & Outdoors","Jewellery & Accessories","Office & Stationery"];

/* ---------- networking ---------- */
async function api(path, body) {
  const o = { headers: {} };
  if (S.token) o.headers["X-Token"] = S.token;
  if (body) { o.method = "POST"; o.headers["Content-Type"] = "application/x-www-form-urlencoded"; o.body = new URLSearchParams(body).toString(); }
  let r, d;
  try { r = await fetch(path, o); d = await r.json(); }
  catch (e) { throw new Error("Cannot reach the server. Is the Java program running?"); }
  if (!r.ok) { const err = new Error(d.error || "Something went wrong"); err.status = r.status; throw err; }
  return d;
}
/* run an action; if the session is missing/expired ask the user to log in, then retry the action */
async function safe(fn) {
  try { await fn(); }
  catch (e) {
    if (e.status === 401) { resetSession(); S.after = fn; openAuth("login", "Please log in to continue"); }
    else toast(e.message, true);
  }
}
function needLogin(fn) { if (S.user) safe(fn); else { S.after = fn; openAuth("login", "Please log in to continue"); } }

function toast(msg, bad) {
  const t = $("toast"); t.textContent = msg; t.className = "toast" + (bad ? " bad" : "");
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add("hidden"), 2800);
}

/* ---------- header / session ---------- */
function syncHeader() {
  $("wishCount").textContent = S.wish.size; $("wishCount").classList.toggle("hidden", S.wish.size === 0);
  $("cartCount").textContent = S.cartCount; $("cartCount").classList.toggle("hidden", S.cartCount === 0);
  $("acctBtn").textContent = S.user ? "Hi, " + S.name.split(" ")[0] + " · Logout" : "Login";
}
function resetSession() { S.token = ""; S.user = ""; S.name = ""; S.wish = new Set(); S.cartCount = 0; localStorage.removeItem("shop_token"); syncHeader(); }
async function refreshMe() {
  if (!S.token) { syncHeader(); return; }
  try {
    const d = await api("/api/me");
    if (!d.loggedIn) resetSession();
    else { S.user = d.user; S.name = d.name; S.wish = new Set(d.wishlist); S.cartCount = d.cartCount; syncHeader(); }
  } catch (e) { syncHeader(); }
}
async function accountClick() {
  if (!S.user) { openAuth("login"); return; }
  if (confirm("Log out of " + S.name + "?")) {
    try { await api("/api/logout", {}); } catch (e) {}
    resetSession(); go("home"); toast("Logged out");
  }
}

/* ---------- login / register modal ---------- */
function openAuth(mode, note) { authTab(mode || "login"); $("authNote").textContent = note || ""; $("authErr").classList.add("hidden"); $("authModal").classList.remove("hidden"); setTimeout(() => $("aUser").focus(), 50); }
function closeAuth() { $("authModal").classList.add("hidden"); S.after = null; }
function authTab(m) {
  S.authMode = m;
  $("tabLogin").classList.toggle("on", m === "login"); $("tabReg").classList.toggle("on", m === "register");
  $("regNameRow").classList.toggle("hidden", m !== "register");
  $("authBtn").textContent = m === "login" ? "Login" : "Create account";
  $("aPass").autocomplete = m === "login" ? "current-password" : "new-password";
}
async function submitAuth() {
  const body = { user: $("aUser").value.trim(), pass: $("aPass").value };
  if (S.authMode === "register") body.name = $("aName").value.trim();
  try {
    const d = await api(S.authMode === "login" ? "/api/login" : "/api/register", body);
    S.token = d.token; localStorage.setItem("shop_token", d.token);
    await refreshMe();
    const next = S.after; S.after = null;
    $("authModal").classList.add("hidden"); $("aPass").value = "";
    toast("Welcome, " + S.name + "!");
    refreshHearts();
    if (next) safe(next);
  } catch (e) { $("authErr").textContent = e.message; $("authErr").classList.remove("hidden"); }
}

/* ---------- navigation ---------- */
function show(view) { $("home").classList.toggle("hidden", view !== "home"); $("page").classList.toggle("hidden", view === "home"); window.scrollTo(0, 0); }
function go(v) {
  closeModal();
  if (v === "home") { show("home"); return; }
  if (!S.user) { S.after = () => go(v); openAuth("login", "Please log in to see your " + v); return; }
  show("page");
  safe(() => ({ wishlist: viewWishlist, cart: viewCart, checkout: viewCheckout, orders: viewOrders })[v]());
}
function page(html) { $("page").innerHTML = html; }

/* ---------- product cards ---------- */
function heartBtn(id) {
  const on = S.wish.has(id);
  return `<button class="heart ${on ? "on" : ""}" data-heart="${id}" title="${on ? "Remove from wishlist" : "Add to wishlist"}" onclick="event.stopPropagation();toggleWish(${id})">${on ? "♥" : "♡"}</button>`;
}
function refreshHearts() {
  document.querySelectorAll("[data-heart]").forEach(b => {
    const id = +b.dataset.heart, on = S.wish.has(id);
    b.classList.toggle("on", on);
    b.firstChild.nodeValue = b.classList.contains("txt") ? (on ? "♥ Wishlisted" : "♡ Add to wishlist") : (on ? "♥" : "♡");
  });
}
function toggleWish(id) {
  needLogin(async () => {
    const d = await api("/api/wishlist/toggle", { id });
    if (d.wished) S.wish.add(id); else S.wish.delete(id);
    syncHeader(); refreshHearts();
    toast(d.wished ? "Added to your wishlist ♥" : "Removed from wishlist");
    if (!$("page").classList.contains("hidden") && $("page").dataset.view === "wishlist") viewWishlist();
  });
}
function priceBlock(p) {
  const multi = p.sellers > 1;
  return `<div class="price">${multi ? '<small class="from">from</small> ' : ""}${money(p.minPrice)} <span class="discount">${p.discount}% off</span></div>
          ${multi ? `<div class="sellerLine">${p.sellers} sellers · ${money(p.minPrice)} – ${money(p.maxPrice)}</div>` : ""}`;
}
function card(p, cartBtn) {
  return `<article class="card" onclick="details(${p.id})">${heartBtn(p.id)}
    <img class="pic" src="${p.image}" loading="lazy" alt="${esc(p.name)}">
    <div class="cardBody"><div class="cat">${esc(p.category)}</div><div class="name">${esc(p.name)}</div>
    <div class="brand">${esc(p.brand)} • ${esc(p.color)}</div>
    <div class="rating">★ ${p.rating} <span class="muted">(${p.reviews.toLocaleString()})</span></div>
    ${priceBlock(p)}
    ${cartBtn ? `<button class="btn small wide" onclick="event.stopPropagation();addToCart(${p.id},'')">Add to cart (best price)</button>` : ""}</div></article>`;
}

/* ---------- product lists (home) ---------- */
function setInfo(h, sub, algo) { $("heading").textContent = h; $("subheading").textContent = sub; $("algo").textContent = algo; $("algo").classList.toggle("hidden", !algo); }
function render() {
  $("grid").innerHTML = S.list.map(p => card(p)).join("") || "<p>No products found.</p>";
  $("moreBtn").classList.toggle("hidden", S.mode === "search" || S.list.length >= S.total);
}
async function fetchList(reset) {
  if (reset) { S.page = 1; S.list = []; }
  const url = S.mode === "search" ? `/api/search?q=${enc(S.q)}&sort=${S.sort}`
    : `/api/products?page=${S.page}&sort=${S.sort}${S.cat ? "&category=" + enc(S.cat) : ""}`;
  const d = await api(url);
  S.total = d.total !== undefined ? d.total : d.count;
  S.list = S.list.concat(d.products);
  return d;
}
const SORT_NOTE = { priceAsc: " Sorted by Randomized QuickSort on each product's cheapest-seller price.", priceDesc: " Sorted by Randomized QuickSort on each product's cheapest-seller price.", rating: " Sorted by Randomized QuickSort on rating." };
function sortNote() { return SORT_NOTE[S.sort] || ""; }

function loadProducts() {
  S.mode = "all"; S.cat = ""; go("home"); $("categories").classList.add("hidden");
  safe(async () => { await fetchList(true); setInfo("All Products", S.total.toLocaleString() + " products from local CSV data", "Data source: data/products.csv + data/sellers.csv • Engine: Java DSA." + sortNote()); render(); });
}
function loadCategory(cat) {
  S.mode = "cat"; S.cat = cat; go("home"); $("categories").classList.add("hidden");
  safe(async () => { await fetchList(true); setInfo(cat, S.total + " products in this category", "Category filtering runs on the file-based catalog." + sortNote()); render(); $("productsTop").scrollIntoView({ behavior: "smooth" }); });
}
function search() {
  const q = $("searchBox").value.trim();
  if (!q) return loadProducts();
  S.mode = "search"; S.q = q; go("home"); $("categories").classList.add("hidden");
  safe(async () => {
    const d = await fetchList(true);
    let note = "KMP exact pattern matching → if nothing matches, Levenshtein Edit Distance DP repairs typos." + sortNote();
    let sub = `${d.count} result${d.count === 1 ? "" : "s"} for “${q}”`;
    if (d.corrected) { sub = `Showing ${d.count} results for “${d.corrected}” (you typed “${q}”)`; note = "No exact match, so Edit Distance DP corrected the spelling to “" + d.corrected + "”." + sortNote(); }
    else if (d.partial) { sub = `No product matches every word of “${q}” — showing products that match some of them`; }
    else if (d.count === 0) { note = "No match found. Try another keyword or browse categories."; }
    setInfo("Search results", sub, note); render(); $("productsTop").scrollIntoView({ behavior: "smooth" });
  });
}
function changeSort() { S.sort = $("sort").value; if (S.mode === "search") search(); else if (S.mode === "cat") loadCategory(S.cat); else loadProducts(); }
function loadMore() { safe(async () => { S.page++; await fetchList(false); render(); }); }
function showCategories() {
  go("home"); $("categories").classList.remove("hidden");
  $("catGrid").innerHTML = CATS.map(c => `<div class="catCard" onclick="loadCategory(this.dataset.c)" data-c="${esc(c)}"><b>${esc(c)}</b><br><small>Browse products →</small></div>`).join("");
  $("categories").scrollIntoView({ behavior: "smooth" });
}
async function loadDeals() {
  try {
    const d = await api("/api/deals");
    $("dealRow").innerHTML = d.products.map(p => {
      const pct = Math.round((p.maxPrice - p.minPrice) * 100 / p.maxPrice);
      return card(p).replace('<article class="card"', '<article class="card deal"').replace('<div class="cardBody">', `<div class="saveTag">Save up to ${pct}%</div><div class="cardBody">`);
    }).join("");
    $("dealsSec").classList.remove("hidden");
  } catch (e) {}
}

/* ---------- product details + seller comparison ---------- */
const stars = r => "★ " + Number(r).toFixed(1);
async function details(id) {
  safe(async () => {
    const d = await api("/api/product?id=" + id), p = d.product, o = d.offers;
    const best = o[0], worst = o[o.length - 1], gap = worst.price - best.price, pct = Math.round(gap * 100 / worst.price);
    const rows = o.map((x, i) => `<tr class="${i === 0 ? "best" : ""}">
        <td><b>${esc(x.seller)}</b><div>${x.tags.map(t => `<span class="chip ${t === "Best price" ? "g" : ""}">${esc(t)}</span>`).join("")}</div></td>
        <td class="rate">${stars(x.sellerRating)}</td>
        <td><b class="big">${money(x.price)}</b>${i > 0 ? `<div class="more">+${money(x.price - best.price)} vs best</div>` : ""}</td>
        <td>${x.deliveryDays} day${x.deliveryDays > 1 ? "s" : ""}${x.stock <= 10 ? `<div class="more warn">Only ${x.stock} left</div>` : ""}</td>
        <td><button class="btn small" data-s="${esc(x.seller)}" onclick="addToCart(${p.id},this.dataset.s)">Add to cart</button></td></tr>`).join("");
    const mini = x => `<div class="mini" onclick="details(${x.id})"><img src="${x.image}" alt=""><div class="mn">${esc(x.name)}</div><div class="mp">${money(x.minPrice)}${x.sellers > 1 ? "+" : ""} · ★ ${x.rating}</div></div>`;
    $("modalContent").innerHTML = `
      <div class="detail"><div class="detailImg"><img src="${p.image}" alt="${esc(p.name)}">${p.realImage ? "" : '<small class="muted">Illustration — drop a real photo into images/real/ to replace it</small>'}</div>
      <div><div class="cat">${esc(p.category)}</div><h1>${esc(p.name)}</h1><div class="brand">${esc(p.brand)} • ${esc(p.color)}</div>
        <div class="rating">★ ${p.rating} <span class="muted">(${p.reviews.toLocaleString()} reviews)</span></div>
        <div class="detailPrice">${money(best.price)} <span class="discount">${p.discount}% off</span></div>
        ${o.length > 1 ? `<div class="saveBox">Sold by <b>${o.length} sellers</b>. Buying from <b>${esc(best.seller)}</b> saves you <b>${money(gap)}</b> (${pct}%) compared with the most expensive seller.</div>` : ""}
        <p>${esc(p.description)}</p>
        <div class="row"><button class="btn" data-s="${esc(best.seller)}" onclick="addToCart(${p.id},this.dataset.s)">Add to cart</button>
        <button class="btn ghost" data-s="${esc(best.seller)}" onclick="buyNow(${p.id},this.dataset.s)">Buy now</button>
        <button class="btn ghost txt ${S.wish.has(p.id) ? "on" : ""}" data-heart="${p.id}" onclick="toggleWish(${p.id})">${S.wish.has(p.id) ? "♥ Wishlisted" : "♡ Add to wishlist"}</button></div>
      </div></div>
      <h2 class="h2">Compare sellers</h2>
      <div class="tableWrap"><table class="offers"><thead><tr><th>Seller</th><th>Seller rating</th><th>Price</th><th>Delivery</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>
      ${d.cheaper.length ? `<div class="recommend"><h2>💡 Cheaper alternatives</h2><div class="recommendGrid">${d.cheaper.map(mini).join("")}</div></div>` : ""}
      <div class="recommend"><h2>Recommended for you</h2><div class="recommendGrid">${d.recommended.map(mini).join("")}</div>
      <p class="note">Recommendations score every product on category, brand, colour, name similarity (Edit Distance) and rating; a Top-K min-heap keeps the best ${d.recommended.length}.</p></div>`;
    $("modal").classList.remove("hidden"); $("modal").querySelector(".modalBox").scrollTop = 0;
  });
}
function closeModal() { $("modal").classList.add("hidden"); }
document.addEventListener("keydown", e => { if (e.key === "Escape") { closeModal(); $("authModal").classList.add("hidden"); } });

/* ---------- cart ---------- */
function addToCart(id, seller, qty) {
  needLogin(async () => {
    const d = await api("/api/cart/add", { id, seller: seller || "", qty: qty || 1 });
    S.cartCount = d.count; syncHeader(); toast("Added to cart 🛒");
  });
}
function buyNow(id, seller) {
  needLogin(async () => { const d = await api("/api/cart/add", { id, seller, qty: 1 }); S.cartCount = d.count; syncHeader(); closeModal(); go("checkout"); });
}
function setQty(id, seller, qty) {
  safe(async () => { const d = await api("/api/cart/update", { id, seller, qty }); S.cartCount = d.count; syncHeader(); viewCart(); });
}
function emptyState(icon, title, text, btn) {
  return `<div class="empty"><div class="eIcon">${icon}</div><h2>${title}</h2><p>${text}</p><button class="btn" onclick="go('home')">${btn}</button></div>`;
}
async function viewCart() {
  $("page").dataset.view = "cart";
  const d = await api("/api/cart"); S.cartCount = d.count; syncHeader();
  if (!d.items.length) { page(emptyState("🛒", "Your cart is empty", "Find a product and compare sellers to get the best price.", "Start shopping")); return; }
  const items = d.items.map(i => `<div class="cartItem"><img src="${i.image}" alt="" onclick="details(${i.id})">
      <div class="ci"><div class="name link" onclick="details(${i.id})">${esc(i.name)}</div>
      <div class="brand">Sold by <b>${esc(i.seller)}</b> · ★ ${i.sellerRating} · delivery in ${i.deliveryDays} day${i.deliveryDays > 1 ? "s" : ""}</div>
      <div class="qtyRow"><div class="qty"><button data-s="${esc(i.seller)}" onclick="setQty(${i.id},this.dataset.s,${i.qty - 1})">−</button><span>${i.qty}</span><button data-s="${esc(i.seller)}" onclick="setQty(${i.id},this.dataset.s,${i.qty + 1})">+</button></div>
      <button class="linkBtn" data-s="${esc(i.seller)}" onclick="setQty(${i.id},this.dataset.s,0)">Remove</button></div></div>
      <div class="ciPrice"><b>${money(i.subtotal)}</b>${i.qty > 1 ? `<div class="more">${money(i.price)} each</div>` : ""}</div></div>`).join("");
  page(`<h1>Shopping cart <small class="muted">(${d.count} item${d.count > 1 ? "s" : ""})</small></h1>
    <div class="twoCol"><div class="box">${items}</div>
    <div class="box summary">${summary(d)}<button class="btn wide" onclick="go('checkout')">Proceed to checkout</button>
    ${d.delivery > 0 ? `<p class="note">Add ${money(500 - d.subtotal)} more for free delivery.</p>` : ""}</div></div>`);
}
function summary(d, lines) {
  return `<h3>Order summary</h3>${lines || ""}<div class="sumRow"><span>Subtotal</span><span>${money(d.subtotal)}</span></div>
    <div class="sumRow"><span>Delivery</span><span>${d.delivery ? money(d.delivery) : '<b class="green">FREE</b>'}</span></div>
    <div class="sumRow total"><span>Total</span><span>${money(d.total)}</span></div>`;
}

/* ---------- wishlist ---------- */
async function viewWishlist() {
  $("page").dataset.view = "wishlist";
  const d = await api("/api/wishlist");
  S.wish = new Set(d.products.map(p => p.id)); syncHeader();
  if (!d.products.length) { page(emptyState("♡", "Your wishlist is empty", "Tap the heart on any product to save it here for later.", "Browse products")); return; }
  page(`<h1>My wishlist <small class="muted">(${d.products.length})</small></h1><div class="grid">${d.products.map(p => card(p, true)).join("")}</div>`);
}

/* ---------- checkout + payment ---------- */
async function viewCheckout() {
  $("page").dataset.view = "checkout";
  const d = await api("/api/cart");
  if (!d.items.length) { go("cart"); return; }
  const lines = d.items.map(i => `<div class="miniLine"><img src="${i.image}" alt=""><div><div class="name">${esc(i.name)}</div><div class="brand">${esc(i.seller)} · Qty ${i.qty}</div></div><b>${money(i.subtotal)}</b></div>`).join("");
  page(`<h1>Checkout</h1><div class="twoCol">
    <div><div class="box"><h3>1. Delivery address</h3>
      <div class="grid2"><div class="field"><label>Full name</label><input id="cName" value="${esc(S.name)}"></div><div class="field"><label>Mobile number</label><input id="cPhone" inputmode="numeric" maxlength="10" placeholder="10-digit number"></div></div>
      <div class="field"><label>Address</label><textarea id="cAddr" rows="2" placeholder="House no, street, area, city"></textarea></div>
      <div class="field" style="max-width:200px"><label>PIN code</label><input id="cPin" inputmode="numeric" maxlength="6" placeholder="6 digits"></div></div>
    <div class="box"><h3>2. Payment method</h3><div class="demo">Demo payment — no real money is charged. Test card: <b>4111 1111 1111 1111</b>, any future expiry, any CVV.</div>
      <div class="payTabs"><label class="pt on" id="pt-upi"><input type="radio" name="pay" value="upi" checked onchange="payTab('upi')">UPI</label><label class="pt" id="pt-card"><input type="radio" name="pay" value="card" onchange="payTab('card')">Credit / Debit card</label><label class="pt" id="pt-cod"><input type="radio" name="pay" value="cod" onchange="payTab('cod')">Cash on Delivery</label></div>
      <div id="pp-upi" class="field"><label>UPI ID</label><input id="cUpi" placeholder="name@bank"></div>
      <div id="pp-card" class="hidden"><div class="field"><label>Card number</label><input id="cCard" inputmode="numeric" maxlength="23" placeholder="1234 5678 9012 3456" oninput="this.value=this.value.replace(/[^\\d ]/g,'').replace(/(\\d{4})(?=\\d)/g,'$1 ').trim()"></div>
        <div class="grid2"><div class="field"><label>Name on card</label><input id="cCardName"></div><div class="grid2"><div class="field"><label>Expiry (MM/YY)</label><input id="cExp" maxlength="5" placeholder="MM/YY" oninput="let v=this.value.replace(/[^\\d]/g,'');if(v.length>2)v=v.slice(0,2)+'/'+v.slice(2);this.value=v"></div><div class="field"><label>CVV</label><input id="cCvv" type="password" inputmode="numeric" maxlength="4" placeholder="•••"></div></div></div></div>
      <div id="pp-cod" class="hidden note">Pay in cash when your order arrives.</div>
      <div id="payErr" class="err hidden"></div></div></div>
    <div class="box summary">${summary(d, lines)}<button id="payBtn" class="btn wide" onclick="placeOrder(${d.total})">Pay ${money(d.total)}</button></div></div>`);
  window.CHECKOUT_TOTAL = d.total;
}
function payTab(m) {
  ["upi", "card", "cod"].forEach(x => { $("pt-" + x).classList.toggle("on", x === m); $("pp-" + x).classList.toggle("hidden", x !== m); });
  $("payBtn").textContent = m === "cod" ? "Place order" : "Pay " + money(window.CHECKOUT_TOTAL);
  $("payErr").classList.add("hidden");
}
function placeOrder() {
  const m = document.querySelector('input[name="pay"]:checked').value;
  const body = { name: $("cName").value, phone: $("cPhone").value, address: $("cAddr").value, pincode: $("cPin").value, method: m };
  if (m === "upi") body.upi = $("cUpi").value;
  if (m === "card") { body.cardNumber = $("cCard").value; body.cardName = $("cCardName").value; body.expiry = $("cExp").value; body.cvv = $("cCvv").value; }
  const btn = $("payBtn"); btn.disabled = true; const label = btn.textContent; btn.textContent = "Processing payment…";
  api("/api/checkout", body).then(d => { S.cartCount = 0; syncHeader(); viewConfirmation(d.order); })
    .catch(e => {
      btn.disabled = false; btn.textContent = label;
      if (e.status === 401) { resetSession(); S.after = () => go("checkout"); openAuth("login", "Your session expired — please log in again"); return; }
      $("payErr").textContent = e.message; $("payErr").classList.remove("hidden"); $("payErr").scrollIntoView({ block: "center", behavior: "smooth" });
    });
}
function viewConfirmation(o) {
  $("page").dataset.view = "confirmed"; window.scrollTo(0, 0);
  page(`<div class="box confirm"><div class="tick">✓</div><h1>Order placed!</h1><p class="muted">Thank you, ${esc(o.name)}. A seller will ship your order soon.</p>
    <div class="kv"><div><span>Order ID</span><b>${esc(o.id)}</b></div><div><span>Payment</span><b>${esc(o.method)}</b></div><div><span>Reference</span><b>${esc(o.payRef)}</b></div><div><span>Total</span><b>${money(o.total)}</b></div></div>
    <p class="brand">Delivering to ${esc(o.name)} · ${esc(o.address)}</p>
    ${o.items.map(i => `<div class="miniLine"><img src="${i.image}" alt=""><div><div class="name">${esc(i.name)}</div><div class="brand">${esc(i.seller)} · Qty ${i.qty}</div></div><b>${money(i.price * i.qty)}</b></div>`).join("")}
    <div class="row center"><button class="btn" onclick="go('orders')">View my orders</button><button class="btn ghost" onclick="go('home')">Continue shopping</button></div></div>`);
}

/* ---------- orders ---------- */
async function viewOrders() {
  $("page").dataset.view = "orders";
  const d = await api("/api/orders");
  if (!d.orders.length) { page(emptyState("▤", "No orders yet", "When you place an order it will show up here.", "Start shopping")); return; }
  page(`<h1>My orders</h1>` + d.orders.map(o => `<div class="box orderCard">
    <div class="orderHead"><div><span>ORDER PLACED</span><b>${new Date(o.time).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}</b></div><div><span>TOTAL</span><b>${money(o.total)}</b></div><div><span>SHIP TO</span><b>${esc(o.name)}</b></div><div class="grow"><span>ORDER # ${esc(o.id)}</span><b class="status ${o.status.startsWith("Paid") ? "paid" : ""}">${esc(o.status)}</b></div></div>
    ${o.items.map(i => `<div class="miniLine"><img src="${i.image}" alt="" onclick="details(${i.id})"><div><div class="name link" onclick="details(${i.id})">${esc(i.name)}</div><div class="brand">Sold by ${esc(i.seller)} · Qty ${i.qty} · ${money(i.price)} each</div></div><b>${money(i.price * i.qty)}</b></div>`).join("")}
    <div class="orderFoot">Payment: <b>${esc(o.method)}</b> · Ref: ${esc(o.payRef)} · Delivery: ${o.delivery ? money(o.delivery) : "Free"} · ${esc(o.address)}</div></div>`).join(""));
}

/* ---------- start ---------- */
(async function init() { await refreshMe(); loadDeals(); loadProducts(); })();
