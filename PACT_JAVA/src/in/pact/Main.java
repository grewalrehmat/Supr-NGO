package in.pact;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Main {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FORM_BYTES = 16_384;
    private final Store store;
    private final Map<String, Session> sessions = new HashMap<>();

    record Session(String email, Instant expires) {}

    private Main(Store store) { this.store = store; }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        Store store = new Store(
            System.getenv().getOrDefault("PACT_DB_URL", "jdbc:postgresql://localhost:5432/pact"),
            System.getenv().getOrDefault("PACT_DB_USER", "pact"),
            System.getenv().getOrDefault("PACT_DB_PASSWORD", "pact-local"),
            Path.of("data", "pact.properties"));
        store.bootstrapReviewer(System.getenv("PACT_REVIEWER_EMAIL"),
                                System.getenv("PACT_REVIEWER_PASSWORD"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        Main app = new Main(store);
        server.createContext("/assets/style.css", app::stylesheet);
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        System.out.println("PACT running at http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                Store.User user = currentUser(exchange);
                if (method.equals("GET")) {
                    show(exchange, path, user);
                } else if (method.equals("POST")) {
                    String origin = exchange.getRequestHeaders().getFirst("Origin");
                    String host = exchange.getRequestHeaders().getFirst("Host");
                    int port = exchange.getLocalAddress().getPort();
                    String localHost = "localhost:" + port;
                    String loopback = "127.0.0.1:" + port;
                    if ((!loopback.equals(host) && !localHost.equals(host))
                            || !("http://" + host).equals(origin)) {
                        throw new Problem(403, "This form must be submitted from the local PACT site.");
                    }
                    submit(exchange, path, user, parseForm(exchange));
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, POST");
                    throw new Problem(405, "This request method is unavailable.");
                }
            } catch (Problem p) {
                send(exchange, p.status, "Request could not be completed",
                     "<section class='card'><p>" + Views.escape(p.getMessage())
                     + "</p><a href='/'>Return to PACT</a></section>", currentUser(exchange));
            } catch (IOException e) {
                System.err.println("PACT request failed: " + e.getClass().getSimpleName());
                send(exchange, 500, "Unable to save or load data",
                     "<p>Try again after checking the local data directory.</p>", currentUser(exchange));
            }
        }
    }

    private void show(HttpExchange exchange, String path, Store.User user) throws IOException {
        switch (path) {
            case "/" -> send(exchange, 200, "People And Community Transformation", Views.home(), user);
            case "/signup" -> send(exchange, 200, "Create an account", Views.signup(), user);
            case "/login" -> send(exchange, 200, "Sign in", Views.login(), user);
            case "/projects" -> {
                Map<String, String> query = decode(exchange.getRequestURI().getRawQuery());
                String theme = query.getOrDefault("theme", "");
                String district = query.getOrDefault("district", "");
                String budget = query.getOrDefault("budget", "");
                long limit = budget.isBlank() ? Long.MAX_VALUE : amount(budget, true);
                send(exchange, 200, "Explore CSR projects",
                     Views.projects(store.match(theme, district, limit), theme, district, budget, user), user);
            }
            case "/events" -> send(exchange, 200, "Community events", Views.events(store.events(), user), user);
            case "/dashboard" -> {
                requireUser(user);
                send(exchange, 200, "Dashboard", Views.dashboard(user, store.forUser(user), store.events(),
                    store.volunteers(), store.tasksFor(user), store.totalDonations()), user);
            }
            default -> throw new Problem(404, "This page does not exist.");
        }
    }

    private void submit(HttpExchange x, String path, Store.User user, Map<String, String> form)
            throws IOException {
        switch (path) {
            case "/signup" -> {
                String password = form.getOrDefault("password", "");
                if (!password.equals(form.get("confirm"))) {
                    throw new Problem(400, "The password confirmation does not match.");
                }
                Store.Role role = switch (form.getOrDefault("role", "")) {
                    case "NGO" -> Store.Role.NGO;
                    case "COMPANY" -> Store.Role.COMPANY;
                    case "DIRECTOR" -> Store.Role.DIRECTOR;
                    case "UNION_HEAD" -> Store.Role.UNION_HEAD;
                    case "VOLUNTEER" -> Store.Role.VOLUNTEER;
                    case "INDIVIDUAL" -> Store.Role.INDIVIDUAL;
                    default -> throw new Problem(400, "Select a valid organisation type.");
                };
                Store.User created = store.register(required(form, "name", 100),
                    required(form, "organisation", 150), required(form, "registration", 80),
                    required(form, "phone", 30), required(form, "email", 254), password, role);
                login(x, created);
                redirect(x, "/dashboard");
            }
            case "/login" -> {
                Store.User account = store.authenticate(required(form, "email", 254),
                    form.getOrDefault("password", ""));
                if (account == null) throw new Problem(401, "The email or password is incorrect.");
                login(x, account);
                redirect(x, "/dashboard");
            }
            case "/logout" -> {
                sessions.remove(sessionToken(x));
                x.getResponseHeaders().add("Set-Cookie", "PACT_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
                redirect(x, "/");
            }
            case "/project" -> {
                requireRole(user, Store.Role.NGO);
                store.propose(user, required(form, "title", 120), required(form, "theme", 80),
                    required(form, "district", 80), amount(required(form, "budget", 14), false),
                    required(form, "summary", 1000));
                redirect(x, "/dashboard");
            }
            case "/review" -> {
                requireRole(user, Store.Role.REVIEWER);
                store.approve(id(form));
                redirect(x, "/dashboard");
            }
            case "/milestone" -> {
                requireRole(user, Store.Role.NGO);
                LocalDate date;
                try { date = LocalDate.parse(required(form, "date", 10)); }
                catch (java.time.format.DateTimeParseException e) {
                    throw new Problem(400, "Enter a valid milestone date.");
                }
                store.addMilestone(user, id(form), date, required(form, "milestone", 120),
                                   required(form, "evidence", 1000));
                redirect(x, "/dashboard");
            }
            case "/event" -> {
                requireRole(user, Store.Role.DIRECTOR);
                LocalDate date;
                try { date = LocalDate.parse(required(form, "date", 10)); }
                catch (java.time.format.DateTimeParseException e) {
                    throw new Problem(400, "Enter a valid event date.");
                }
                String goalMetric = form.getOrDefault("goalMetric", "").strip();
                String goalTargetRaw = form.getOrDefault("goalTarget", "").strip();
                long goalTarget = 0;
                if (!goalMetric.isEmpty() || !goalTargetRaw.isEmpty()) {
                    if (goalMetric.isEmpty() || goalMetric.length() > 80 || goalTargetRaw.isEmpty()) {
                        throw new Problem(400, "Enter both a progress measure and its target.");
                    }
                    goalTarget = amount(goalTargetRaw, false);
                }
                store.createEvent(required(form, "title", 120), required(form, "type", 40),
                    required(form, "location", 120), date, amount(required(form, "budget", 14), true),
                    required(form, "description", 1000), goalMetric, goalTarget);
                redirect(x, "/dashboard");
            }
            case "/task" -> {
                requireRole(user, Store.Role.UNION_HEAD);
                store.assignTask(id(form), required(form, "title", 120),
                    required(form, "description", 500), required(form, "volunteer", 254));
                redirect(x, "/dashboard");
            }
            case "/event-progress" -> {
                requireRole(user, Store.Role.UNION_HEAD);
                store.updateEventProgress(id(form), amount(required(form, "progress", 14), true),
                    required(form, "status", 20));
                redirect(x, "/dashboard");
            }
            case "/expense" -> {
                requireRole(user, Store.Role.DIRECTOR);
                store.recordEventExpense(id(form), amount(required(form, "amount", 14), false));
                redirect(x, "/dashboard");
            }
            case "/donate" -> {
                requireUser(user);
                String targetType = required(form, "targetType", 10);
                store.donate(user, targetType, id(form), amount(required(form, "amount", 14), false));
                redirect(x, targetType.equals("EVENT") ? "/events" : "/projects");
            }
            default -> throw new Problem(404, "This action does not exist.");
        }
    }

    private void login(HttpExchange x, Store.User user) {
        sessions.remove(sessionToken(x));
        Instant now = Instant.now();
        sessions.values().removeIf(s -> !s.expires().isAfter(now));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user.email(), now.plusSeconds(7200)));
        x.getResponseHeaders().add("Set-Cookie",
            "PACT_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=7200");
    }

    private Store.User currentUser(HttpExchange exchange) {
        String token = sessionToken(exchange);
        Session session = sessions.get(token);
        if (session == null) return null;
        if (!session.expires().isAfter(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return store.user(session.email());
    }

    private static String sessionToken(HttpExchange x) {
        String cookies = x.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String item : cookies.split(";")) {
                String cookie = item.strip();
                if (cookie.startsWith("PACT_SESSION=")) return cookie.substring(13);
            }
        }
        return "";
    }

    private static Map<String, String> parseForm(HttpExchange x) throws IOException {
        String contentType = x.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";")[0].equals("application/x-www-form-urlencoded")) {
            throw new Problem(415, "Use the PACT web form to submit this request.");
        }
        byte[] bytes = x.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (bytes.length > MAX_FORM_BYTES) throw new Problem(413, "The submitted form is too large.");
        return decode(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Map<String, String> decode(String input) {
        Map<String, String> values = new HashMap<>();
        if (input == null || input.isEmpty()) return values;
        try {
            for (String pair : input.split("&")) {
                String[] entry = pair.split("=", 2);
                if (entry.length == 2) {
                    values.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException e) {
            throw new Problem(400, "The request contains an invalid field encoding.");
        }
        return values;
    }

    private static String required(Map<String, String> values, String key, int limit) {
        String value = values.getOrDefault(key, "").strip();
        if (value.isEmpty() || value.length() > limit) {
            throw new Problem(400, "Enter a valid value for " + key + " (up to " + limit + " characters).");
        }
        return value;
    }

    private static long amount(String value, boolean allowZero) {
        try {
            long amount = Long.parseLong(value.strip());
            if (amount >= (allowZero ? 0 : 1) && amount <= 999_999_999_999L) return amount;
        } catch (NumberFormatException ignored) {
        }
        throw new Problem(400, "Enter a budget in whole rupees within the supported range.");
    }

    private static long id(Map<String, String> form) {
        try { return Long.parseLong(form.getOrDefault("id", "")); }
        catch (NumberFormatException e) { throw new Problem(400, "The project ID is invalid."); }
    }

    private static void requireUser(Store.User user) {
        if (user == null) throw new Problem(401, "Sign in to access this page.");
    }

    private static void requireRole(Store.User user, Store.Role role) {
        requireUser(user);
        if (user.role() != role) throw new Problem(403, "Your account cannot perform this action.");
    }

    private static void redirect(HttpExchange x, String location) throws IOException {
        x.getResponseHeaders().set("Location", location);
        x.sendResponseHeaders(303, -1);
    }

    private void stylesheet(HttpExchange x) throws IOException {
        try (x) { write(x, 200, "text/css", Views.CSS); }
    }

    private static void send(HttpExchange x, int status, String title, String body, Store.User user)
            throws IOException {
        write(x, status, "text/html", Views.layout(title, body, user));
    }

    private static void write(HttpExchange x, int status, String type, String body) throws IOException {
        var headers = x.getResponseHeaders();
        headers.set("Content-Type", type + "; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
    }
}

final class Problem extends RuntimeException {
    final int status;
    Problem(int status, String message) {
        super(message);
        this.status = status;
    }
}
