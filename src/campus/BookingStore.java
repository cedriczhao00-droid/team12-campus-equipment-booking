package campus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class BookingStore {
    record State(List<Booking> bookings, List<QueueEntry> queue) {}
    private final Path file;

    BookingStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    List<Booking> load() throws IOException {
        return loadState().bookings();
    }

    State loadState() throws IOException {
        if (Files.notExists(file)) {
            return new State(List.of(), List.of());
        }
        List<Booking> bookings = new ArrayList<>();
        List<QueueEntry> queue = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            try {
                if (fields.length == 6 && fields[0].equals("Q")) {
                    queue.add(new QueueEntry(fields[1], fields[2], fields[3],
                            QueueStatus.valueOf(fields[4]), optionalTime(fields[5])));
                } else if (fields.length == 5 || fields.length == 6) {
                    bookings.add(new Booking(fields[0], fields[1], fields[2],
                            Instant.parse(fields[3]), Instant.parse(fields[4]),
                            fields.length == 6 ? optionalTime(fields[5]) : null));
                } else {
                    throw new IllegalArgumentException("Unexpected field count");
                }
            } catch (RuntimeException ex) {
                throw new IOException("Invalid booking data in " + file, ex);
            }
        }
        return new State(List.copyOf(bookings), List.copyOf(queue));
    }

    private Instant optionalTime(String value) {
        return value.isEmpty() ? null : Instant.parse(value);
    }

    void save(List<Booking> bookings, List<QueueEntry> queue) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "bookings-", ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            for (Booking booking : bookings) {
                lines.add(String.join("\t", booking.reference(), booking.student(), booking.slotId(),
                        booking.start().toString(), booking.end().toString(),
                        booking.cancelledAt() == null ? "" : booking.cancelledAt().toString()));
            }
            for (QueueEntry entry : queue) {
                lines.add(String.join("\t", "Q", entry.id(), entry.student(), entry.slotId(),
                        entry.status().name(), entry.deadline() == null ? "" : entry.deadline().toString()));
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
