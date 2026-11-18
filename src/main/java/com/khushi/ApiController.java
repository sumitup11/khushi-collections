package com.khushi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
  public record Line(long id, int q) {}
  public record OrderReq(List<Line> items, String name, String phone, String addr, String pay) {}
  public record Verify(String orderId, String razorpay_order_id, String razorpay_payment_id, String razorpay_signature) {}

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
  private final UserRepo users; private final ProductRepo prods; private final OrdersRepo orders;
  private final Jwt jwt; private final ObjectMapper om;
  private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
  @Value("${app.razorpay.key}") private String rzpKey;
  @Value("${app.razorpay.secret}") private String rzpSecret;

  public ApiController(UserRepo users, ProductRepo prods, OrdersRepo orders, Jwt jwt, ObjectMapper om) {
    this.users = users; this.prods = prods; this.orders = orders; this.jwt = jwt; this.om = om;
  }

  static ResponseStatusException err(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
  static String str(Map<String, String> b, String k) { String v = b.get(k); return v == null ? "" : v.trim(); }

  Map<String, Object> authOut(User u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("token", jwt.make(u.email)); m.put("name", u.name); m.put("email", u.email);
    return m;
  }

  User me(String h) {
    String e = (h != null && h.startsWith("Bearer ")) ? jwt.email(h.substring(7)) : null;
    return Optional.ofNullable(e).flatMap(users::findByEmail)
        .orElseThrow(() -> err(HttpStatus.UNAUTHORIZED, "Please log in again"));
  }

  @PostMapping("/auth/register")
  public Map<String, Object> register(@RequestBody Map<String, String> b) {
    String name = str(b, "name"), email = str(b, "email").toLowerCase(), pw = str(b, "password");
    if (name.length() < 2) throw err(HttpStatus.BAD_REQUEST, "Please enter your name (at least 2 characters)");
    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw err(HttpStatus.BAD_REQUEST, "Please enter a valid email address");
    if (pw.length() < 6 || pw.length() > 72) throw err(HttpStatus.BAD_REQUEST, "Password must be 6 to 72 characters");
    if (users.findByEmail(email).isPresent()) throw err(HttpStatus.CONFLICT, "This email is already registered, please log in");
    User u = new User();
    u.name = name; u.email = email; u.passwordHash = enc.encode(pw);
    users.save(u);
    return authOut(u);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody Map<String, String> b) {
    User u = users.findByEmail(str(b, "email").toLowerCase()).orElse(null);
    if (u == null || !enc.matches(str(b, "password"), u.passwordHash)) throw err(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
    return authOut(u);
  }

  @GetMapping("/products")
  public List<Product> products() { return prods.findAll(); }

  @GetMapping("/orders/mine")
  public List<Map<String, Object>> mine(@RequestHeader(value = "Authorization", required = false) String h) throws Exception {
    User u = me(h);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Orders o : orders.findByEmailOrderByIdDesc(u.email)) out.add(view(o));
    return out;
  }

  @PostMapping("/orders")
  public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String h,
                                    @RequestBody OrderReq r) throws Exception {
    User u = me(h);
    if (r.items() == null || r.items().isEmpty() || r.items().size() > 30) throw err(HttpStatus.BAD_REQUEST, "Your cart is empty");
    if (r.name() == null || r.name().trim().length() < 2) throw err(HttpStatus.BAD_REQUEST, "Please enter your name");
    if (r.phone() == null || !r.phone().trim().matches("[6-9]\\d{9}")) throw err(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number");
    if (r.addr() == null || r.addr().trim().length() < 10) throw err(HttpStatus.BAD_REQUEST, "Please enter your full address with PIN code");
    boolean online = "ONLINE".equals(r.pay());
    if (online && (rzpKey.isBlank() || rzpSecret.isBlank()))
      throw err(HttpStatus.SERVICE_UNAVAILABLE, "Online payment is not configured. Please choose Cash on Delivery.");
    List<Map<String, Object>> lines = new ArrayList<>();
    int sub = 0;
    for (Line l : r.items()) {
      Product p = prods.findById(l.id()).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Product not found"));
      if (l.q() < 1 || l.q() > 20) throw err(HttpStatus.BAD_REQUEST, "Invalid quantity");
      sub += p.price * l.q();
      lines.add(Map.of("n", p.name, "q", l.q(), "price", p.price));
    }
    Orders o = new Orders();
    o.email = u.email; o.itemsJson = om.writeValueAsString(lines); o.total = sub + (sub < 999 ? 49 : 0);
    o.name = r.name().trim(); o.phone = r.phone().trim(); o.addr = r.addr().trim();
    o.pay = online ? "ONLINE" : "COD"; o.status = online ? "PENDING_PAYMENT" : "COD_PLACED"; o.createdAt = Instant.now();
    orders.save(o);
    Map<String, Object> m = new LinkedHashMap<>(view(o));
    if (online) {
      try { o.rzpOrderId = rzpCreate(o); orders.save(o); }
      catch (Exception e) {
        orders.delete(o);
        throw err(HttpStatus.BAD_GATEWAY, "Could not start online payment. Please try again or choose Cash on Delivery.");
      }
      m.put("razorpayOrderId", o.rzpOrderId); m.put("amount", o.total * 100); m.put("keyId", rzpKey);
    }
    return m;
  }

  @PostMapping("/orders/verify")
  public Map<String, Object> verify(@RequestHeader(value = "Authorization", required = false) String h,
                                    @RequestBody Verify v) throws Exception {
    User u = me(h);
    long id;
    try { id = Long.parseLong(v.orderId().substring(2)) - 100000; }
    catch (Exception e) { throw err(HttpStatus.BAD_REQUEST, "Invalid order"); }
    Orders o = orders.findById(id).filter(x -> x.email.equals(u.email))
        .orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Order not found"));
    if (o.rzpOrderId == null || !o.rzpOrderId.equals(v.razorpay_order_id()) || v.razorpay_payment_id() == null || v.razorpay_signature() == null)
      throw err(HttpStatus.BAD_REQUEST, "Payment does not match this order");
    String expected = hmac(o.rzpOrderId + "|" + v.razorpay_payment_id(), rzpSecret);
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v.razorpay_signature().getBytes(StandardCharsets.UTF_8)))
      throw err(HttpStatus.BAD_REQUEST, "Payment verification failed");
    o.status = "PAID"; o.rzpPaymentId = v.razorpay_payment_id();
    orders.save(o);
    return view(o);
  }

  Map<String, Object> view(Orders o) throws Exception {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", "KC" + (100000 + o.id)); m.put("items", om.readValue(o.itemsJson, List.class));
    m.put("total", o.total); m.put("addr", o.addr); m.put("status", o.status);
    m.put("pay", "PAID".equals(o.status) ? "Paid online" : "COD_PLACED".equals(o.status) ? "Cash on Delivery" : "Payment pending");
    m.put("date", FMT.format(o.createdAt));
    return m;
  }

  String rzpCreate(Orders o) throws Exception {
    String body = om.writeValueAsString(Map.of("amount", o.total * 100, "currency", "INR", "receipt", "kc_" + o.id));
    String basic = Base64.getEncoder().encodeToString((rzpKey + ":" + rzpSecret).getBytes(StandardCharsets.UTF_8));
    HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
        .header("Content-Type", "application/json").header("Authorization", "Basic " + basic)
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) throw new IllegalStateException("Razorpay error " + res.statusCode());
    return om.readTree(res.body()).get("id").asText();
  }

  static String hmac(String data, String key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    StringBuilder sb = new StringBuilder();
    for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}
