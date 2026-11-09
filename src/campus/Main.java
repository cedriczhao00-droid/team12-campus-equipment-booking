package campus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public final class Main {
    private static final Set<String> STUDENTS = Set.of("student-a", "student-b");

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
        System.out.println("Campus Equipment Desk: http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void handle(HttpExchange exchange, Clock clock, DemoSchedule schedule,
                               BookingService service) throws IOException {
        try (exchange) {
            String route = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (route.equals("/") && method.equals("GET")) {
                Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
                LocalDate today = LocalDate.now(clock);
                LocalDate date = selectedDate(query.get("date"), today);
                String student = query.getOrDefault("student", "student-a");
                if (!STUDENTS.contains(student)) {
                    student = "student-a";
                }
                BookingResult result = findResult(query.get("result"));
                send(exchange, 200, "text/html", BookingPage.render(date, today, student, result, schedule, service));
            } else if (route.equals("/book") && method.equals("POST")) {
                book(exchange, clock, service);
            } else if (route.equals("/style.css") && method.equals("GET")) {
                send(exchange, 200, "text/css", BookingPage.STYLE);
            } else if (route.equals("/") || route.equals("/book") || route.equals("/style.css")) {
                exchange.getResponseHeaders().set("Allow", route.equals("/book") ? "POST" : "GET");
                send(exchange, 405, "text/plain", "Method not allowed.");
            } else {
                send(exchange, 404, "text/plain", "Page not found.");
            }
        }
    }

    private static void book(HttpExchange exchange, Clock clock, BookingService service) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            send(exchange, 413, "text/plain", "Booking request is too large.");
            return;
        }
        Map<String, String> fields = form(new String(body, StandardCharsets.UTF_8));
        String student = fields.getOrDefault("student", "");
        if (!STUDENTS.contains(student)) {
            send(exchange, 400, "text/plain", "Choose a demo student before booking.");
            return;
        }
        BookingResult result;
        try {
            result = service.book(student, fields.get("slot"));
        } catch (IOException ex) {
            System.err.println("Could not save booking: " + ex.getMessage());
            send(exchange, 503, "text/plain", "The booking could not be saved. Try again.");
            return;
        }
        LocalDate date = selectedDate(fields.get("date"), LocalDate.now(clock));
        exchange.getResponseHeaders().set("Location", "/?date=" + date + "&student="
                + URLEncoder.encode(student, StandardCharsets.UTF_8) + "&result=" + result.name());
        exchange.sendResponseHeaders(303, -1);
    }

    private static LocalDate selectedDate(String value, LocalDate today) {
        if (value != null) {
            try {
                LocalDate date = LocalDate.parse(value);
                if (!date.isBefore(today) && !date.isAfter(today.plusDays(14))) {
                    return date;
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // An invalid filter falls back to the default day.
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
