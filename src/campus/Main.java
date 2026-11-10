package campus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Main {
    private static final Set<String> STUDENTS = Set.of("student-a", "student-b", "student-c");
    private static final Set<String> ACTIONS = Set.of("/book", "/cancel", "/join", "/accept", "/leave");

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path data = args.length > 1 ? Path.of(args[1]) : Path.of("data", "bookings.tsv");
        Clock clock = Clock.system(DemoSchedule.ZONE);
        DemoSchedule schedule = new DemoSchedule();
        BookingService service = new BookingService(clock, schedule, new BookingStore(data));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", exchange -> handle(exchange, clock, schedule, service));
        server.start();
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                service.refreshOffers();
            } catch (IOException ex) {
                System.err.println("Could not update expired offers: " + ex.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
        System.out.println("Campus Equipment Desk: http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void handle(HttpExchange exchange, Clock clock, DemoSchedule schedule,
                               BookingService service) throws IOException {
        try (exchange) {
            String route = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (route.equals("/") && method.equals("GET")) {
                try {
                    service.refreshOffers();
                } catch (IOException ex) {
                    send(exchange, 503, "text/plain", "The schedule could not be refreshed. Try again.");
                    return;
                }
                Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
                LocalDate today = LocalDate.now(clock);
                LocalDate date = selectedDate(query.get("date"), today);
                String student = query.getOrDefault("student", "student-a");
                if (!STUDENTS.contains(student)) {
                    student = "student-a";
                }
                BookingResult result = findResult(query.get("result"));
                send(exchange, 200, "text/html", BookingPage.render(date, today, student, result, schedule, service));
            } else if (ACTIONS.contains(route) && method.equals("POST")) {
                changeAllocation(exchange, clock, service, route);
            } else if (route.equals("/style.css") && method.equals("GET")) {
                send(exchange, 200, "text/css", BookingPage.STYLE);
            } else if (route.equals("/") || ACTIONS.contains(route) || route.equals("/style.css")) {
                exchange.getResponseHeaders().set("Allow", ACTIONS.contains(route) ? "POST" : "GET");
                send(exchange, 405, "text/plain", "Method not allowed.");
            } else {
                send(exchange, 404, "text/plain", "Page not found.");
            }
        }
    }

    private static void changeAllocation(HttpExchange exchange, Clock clock, BookingService service,
                                         String route) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            send(exchange, 413, "text/plain", "Booking request is too large.");
            return;
        }
        Map<String, String> fields = form(new String(body, StandardCharsets.UTF_8));
        String student = fields.getOrDefault("student", "");
        if (!STUDENTS.contains(student)) {
            send(exchange, 400, "text/plain", "Choose a demo student and try again.");
            return;
        }
        BookingResult result;
        try {
            result = switch (route) {
                case "/book" -> service.book(student, fields.get("slot"));
                case "/cancel" -> service.cancel(student, fields.get("reference"));
                case "/join" -> service.join(student, fields.get("slot"));
                case "/accept" -> service.resolve(student, fields.get("entry"), true);
                case "/leave" -> service.resolve(student, fields.get("entry"), false);
                default -> throw new IllegalArgumentException("Unexpected action");
            };
        } catch (IOException ex) {
            System.err.println("Could not save booking: " + ex.getMessage());
            send(exchange, 503, "text/plain", "The booking could not be saved. Try again.");
            return;
        }
        LocalDate date = selectedDate(fields.get("date"), LocalDate.now(clock));
        exchange.getResponseHeaders().set("Location", "/?date=" + date + "&student="
                + student + "&result=" + result.name());
        exchange.sendResponseHeaders(303, -1);
    }

    private static LocalDate selectedDate(String value, LocalDate today) {
        if (value != null) {
            try {
                LocalDate date = LocalDate.parse(value);
                if (!date.isBefore(today) && !date.isAfter(today.plusDays(14))) {
                    return date;
                }
            } catch (DateTimeParseException ignored) {
                // Show tomorrow if the date is invalid.
            }
        }
        return today.plusDays(1);
    }

    private static BookingResult findResult(String name) {
        for (BookingResult result : BookingResult.values()) {
            if (result.name().equals(name)) {
                return result;
            }
        }
        return null;
    }

    private static Map<String, String> form(String value) {
        Map<String, String> fields = new HashMap<>();
        if (value != null && value.length() <= 4096) {
            for (String pair : value.split("&")) {
                String[] entry = pair.split("=", 2);
                if (entry.length == 2) {
                    try {
                        fields.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException ignored) {
                        return Map.of();
                    }
                }
            }
        }
        return fields;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
