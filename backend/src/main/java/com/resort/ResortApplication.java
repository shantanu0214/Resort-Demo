package com.resort;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class ResortApplication {
  public static void main(String[] a) {
    SpringApplication.run(ResortApplication.class, a);
  }

  // ---------- Entities ----------
  @Entity(name = "Room")
  public static class Room {
    @Id
    @GeneratedValue
    public Long id;
    public String name, type, image;
    public int capacity;
    public double price;

    public Room() {
    }

    public Room(String n, String t, int c, double p, String img) {
      name = n;
      type = t;
      capacity = c;
      price = p;
      image = img;
    }
  }

  @Entity(name = "Booking")
  public static class Booking {
    @Id
    @GeneratedValue
    public Long id;
    public Long roomId;
    public String guestName, email, phone;
    public LocalDate checkIn, checkOut;
    public double total;
    public String status;
    public int guests = 1;
  }

  // ---------- Repos ----------
  public interface RoomRepo extends JpaRepository<Room, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) // row lock: stops double booking under concurrent requests
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") Long id);
  }

  public interface BookingRepo extends JpaRepository<Booking, Long> {
    List<Booking> findByEmailIgnoreCase(String email);

    @Query("select b.roomId from Booking b where b.status <> 'CANCELLED' and b.checkIn < :co and b.checkOut > :ci")
    List<Long> busyHouse(@Param("ci") LocalDate ci, @Param("co") LocalDate co);
  }

  // ---------- Dynamic pricing ----------
  static double nightRate(double base, LocalDate d) {
    double m = 1.0;
    DayOfWeek w = d.getDayOfWeek();
    if (w == DayOfWeek.FRIDAY || w == DayOfWeek.SATURDAY)
      m += 0.20; // weekend
    if (d.getMonthValue() >= 10 || d.getMonthValue() <= 2)
      m += 0.15; // peak season Oct-Feb
    if (ChronoUnit.DAYS.between(LocalDate.now(), d) <= 3)
      m += 0.10; // last-minute
    return Math.round(base * m);
  }

  static double quote(double base, LocalDate in, LocalDate out) {
    double t = 0;
    for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1))
      t += nightRate(base, d);
    return t;
  }

  // ---------- Booking service (transactional + row lock) ----------
  @org.springframework.stereotype.Service
  public static class BookingService {
    final RoomRepo House;
    final BookingRepo bookings;

    public BookingService(RoomRepo r, BookingRepo b) {
      House = r;
      bookings = b;
    }

    @org.springframework.transaction.annotation.Transactional
    public Booking create(Booking b) {
      if (b.checkIn == null || b.checkOut == null || !b.checkOut.isAfter(b.checkIn)
          || b.checkIn.isBefore(LocalDate.now()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dates");
      Room r = House.lockById(b.roomId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
      if (b.guests < 1 || b.guests > r.capacity)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid guest count");
      if (bookings.busyHouse(b.checkIn, b.checkOut).contains(b.roomId)) // checked AFTER lock
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Room not available");
      b.id = null;
      b.total = quote(r.price, b.checkIn, b.checkOut) * b.guests;
      b.status = "CONFIRMED"; // MOCK payment: real gateway (Razorpay) future phase
      return bookings.save(b);
    }
  }

  // ---------- API ----------
  @RestController
  @RequestMapping("/api")
  public static class Api {
    final RoomRepo House;
    final BookingRepo bookings;
    final BookingService svc;

    Api(RoomRepo r, BookingRepo b, BookingService s) {
      House = r;
      bookings = b;
      svc = s;
    }

    @GetMapping("/rooms")
    List<Room> all() {
      return House.findAll();
    }

    @GetMapping("/rooms/available")
    List<Room> available(@RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut) {
      List<Long> busy = bookings.busyHouse(checkIn, checkOut);
      return House.findAll().stream().filter(r -> !busy.contains(r.id)).toList();
    }

    @PostMapping("/bookings")
    Booking book(@RequestBody Booking b) {
      return svc.create(b);
    }

    @GetMapping("/quote")
    Map<String, Object> quoteApi(@RequestParam Long roomId, @RequestParam LocalDate checkIn,
        @RequestParam LocalDate checkOut, @RequestParam(defaultValue = "1") int guests) {
      Room r = House.findById(roomId).orElseThrow();
      double t = quote(r.price, checkIn, checkOut) * guests;
      long n = ChronoUnit.DAYS.between(checkIn, checkOut);
      return Map.of("total", t, "avg", n > 0 ? Math.round(t / n) : 0, "nights", n);
    }

    @GetMapping("/bookings")
    List<Booking> mine(@RequestParam String email) {
      return bookings.findByEmailIgnoreCase(email);
    }

    // ---- Admin (Basic auth) ----
    @GetMapping("/admin/bookings")
    List<Booking> allBookings() {
      return bookings.findAll();
    }

    @PutMapping("/admin/bookings/{id}/cancel")
    Booking cancel(@PathVariable Long id) {
      Booking b = bookings.findById(id).orElseThrow();
      b.status = "CANCELLED";
      return bookings.save(b);
    }

    @PostMapping("/admin/rooms")
    Room addRoom(@RequestBody Room r) {
      r.id = null;
      return House.save(r);
    }

    @GetMapping("/admin/stats")
    Map<String, Object> stats() {
      var live = bookings.findAll().stream().filter(b -> !"CANCELLED".equals(b.status)).toList();
      LocalDate t = LocalDate.now();
      long today = live.stream().filter(b -> !b.checkIn.isAfter(t) && b.checkOut.isAfter(t)).count();
      return Map.of("rooms", House.count(), "bookings", live.size(),
          "revenue", live.stream().mapToDouble(b -> b.total).sum(), "occupiedToday", today);
    }
  }

  // ---------- AI Concierge (Claude API via backend proxy; key stays server-side)
  // ----------
  @RestController
  @RequestMapping("/api")
  public static class Concierge {
    final RoomRepo House;
    final RestClient http = RestClient.create();
    final Map<String, Deque<Long>> hits = new java.util.concurrent.ConcurrentHashMap<>();

    Concierge(RoomRepo r) {
      House = r;
    }

    boolean limited(String ip) { // max 15 messages / minute / IP
      long now = System.currentTimeMillis();
      Deque<Long> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
      synchronized (q) {
        while (!q.isEmpty() && now - q.peekFirst() > 60_000)
          q.pollFirst();
        if (q.size() >= 15)
          return true;
        q.addLast(now);
        return false;
      }
    }

    static boolean has(String t, String... w) {
      for (String x : w)
        if (t.contains(x))
          return true;
      return false;
    }

    static String names(List<Room> l) {
      return l.stream().map(r -> r.name + " (from Rs " + (long) r.price + "/night, up to " + r.capacity + " guests)")
          .collect(java.util.stream.Collectors.joining("; "));
    }

    // Rule-based replies from live room data (used when no API key is set). Not AI.
    Map<String, String> demoReply(String q) {
      String t = q.toLowerCase();
      List<Room> all = House.findAll();
      var m = java.util.regex.Pattern.compile("(\\d{4,6})").matcher(t.replace(",", ""));
      String out;
      if (m.find()) {
        double budget = Double.parseDouble(m.group(1));
        List<Room> ok = all.stream().filter(r -> r.price <= budget).toList();
        out = ok.isEmpty()
            ? "No room fits that budget. Our cheapest room starts at Rs "
                + (long) all.stream().mapToDouble(r -> r.price).min().orElse(0) + " per night."
            : "House with a base rate up to Rs " + (long) budget + ": " + names(ok) + ".";
      } else if (has(t, "family", "kids", "children", "group", "parivar", "bachche")) {
        out = "Great for families and groups: " + names(all.stream().filter(r -> r.capacity >= 4).toList()) + ".";
      } else if (has(t, "cheap", "budget", "sasta", "affordable", "low")) {
        out = "Budget-friendly options: "
            + names(all.stream().sorted(Comparator.comparingDouble(r -> r.price)).limit(2).toList()) + ".";
      } else if (has(t, "luxury", "best", "premium", "villa", "honeymoon", "romantic")) {
        out = "Our most luxurious options: "
            + names(all.stream().sorted(Comparator.comparingDouble((Room r) -> r.price).reversed()).limit(2).toList())
            + ".";
      } else if (has(t, "price", "rate", "weekend", "kimat", "dar", "cost")) {
        out = "Rates are per night. Weekends (Fri/Sat) are +20%, Oct to Feb is +15%, and last-minute stays (within 3 days) are +10%. Pick your dates in the Book tab to see the exact total.";
      } else if (has(t, "book", "reserve")) {
        out = "Open the Book tab, choose your dates, pick an available room and fill in your details.";
      } else {
        out = "I can help with room suggestions, budgets and pricing. Try: 'room for a family', 'room under 8000' or 'weekend price'.";
      }
      return Map.of("reply", out, "mode", "demo");
    }

    @PostMapping("/concierge")
    Map<String, String> chat(@RequestBody Map<String, List<Map<String, String>>> body, HttpServletRequest req) {
      String ip = Optional.ofNullable(req.getHeader("X-Forwarded-For")).map(h -> h.split(",")[0].trim())
          .orElse(req.getRemoteAddr());
      if (limited(ip))
        return Map.of("reply", "Too many messages. Please wait a minute 🙏");
      String key = System.getenv("ANTHROPIC_API_KEY");
      boolean demo = key == null || key.isBlank(); // no API key -> rule-based demo mode

      List<Map<String, String>> msgs = new ArrayList<>();
      for (var m : body.getOrDefault("messages", List.of())) {
        String role = m.get("role"), c = m.get("content");
        if (c == null || c.isBlank() || !("user".equals(role) || "assistant".equals(role)))
          continue;
        msgs.add(Map.of("role", role, "content", c.length() > 800 ? c.substring(0, 800) : c));
      }
      if (msgs.size() > 10)
        msgs = new ArrayList<>(msgs.subList(msgs.size() - 10, msgs.size()));
      while (!msgs.isEmpty() && !"user".equals(msgs.get(0).get("role")))
        msgs.remove(0);
      if (msgs.isEmpty())
        return Map.of("reply", "How can I help?");
      if (demo)
        return demoReply(msgs.get(msgs.size() - 1).get("content"));

      String roomList = House.findAll().stream()
          .map(r -> r.name + " (" + r.type + ", up to " + r.capacity + " guests, from Rs " + (long) r.price + "/night)")
          .collect(java.util.stream.Collectors.joining("; "));
      String sys = "You are the friendly AI concierge of Green Shadow Resort. Today is " + LocalDate.now() + ". House: "
          + roomList
          + ". Pricing: base rate per night, +20% on Fri/Sat, +15% from Oct to Feb, +10% if the stay starts within 3 days. "
          + "Help guests choose a room, estimate prices and plan their stay. To book, tell them to use the Book tab. "
          + "Do not invent facts such as check-in times, policies, menus or exact local place names; if unsure, say the front desk will confirm. "
          + "Reply in the guest's language (English, Hindi or Marathi) in at most 4 short sentences.";
      try {
        Map<?, ?> res = http.post().uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key).header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("model", System.getenv().getOrDefault("CLAUDE_MODEL", "claude-haiku-5-5"),
                "max_tokens", 400, "system", sys, "messages", msgs))
            .retrieve().body(Map.class);
        Map<?, ?> first = (Map<?, ?>) ((List<?>) res.get("content")).get(0);
        return Map.of("reply", String.valueOf(first.get("text")), "mode", "ai");
      } catch (Exception e) {
        return Map.of("reply", "Sorry, something went wrong. Please try again shortly.");
      }
    }
  }

  // ---------- Security + CORS ----------
  @Configuration
  public static class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
      http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
          .authorizeHttpRequests(a -> a.requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().permitAll())
          .httpBasic(Customizer.withDefaults());
      return http.build();
    }

    @Bean
    UserDetailsService users() {
      return new InMemoryUserDetailsManager(User.withUsername("admin")
          .password("{noop}" + System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123")).roles("ADMIN").build());
    }

    @Bean
    WebMvcConfigurer cors() {
      return new WebMvcConfigurer() {
        public void addCorsMappings(CorsRegistry r) {
          r.addMapping("/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*");
        }
      };
    }
  }

  // ---------- Seed data ----------
  @Bean
  CommandLineRunner seed(RoomRepo House) {
    return x -> {
      String u = "https://picsum.photos/seed/";
      House.saveAll(List.of(
          new Room("Forest Deluxe", "Deluxe", 2, 2000, u + "r1/600/380"),
          new Room("Lake View Suite", "Suite", 3, 2000, u + "r2/600/380"),
          new Room("Treehouse Villa", "Villa", 4, 2000, u + "r3/600/380"),
          new Room("Garden Cottage", "Cottage", 2, 2000, u + "r4/600/380"),
          new Room("Family Suite", "Suite", 5, 2000, u + "r5/600/380"),
          new Room("Presidential Villa", "Villa", 6, 2000, u + "r6/600/380")));
    };
  }
}
