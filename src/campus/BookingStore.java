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
    private final Path file;

    BookingStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    List<Booking> load() throws IOException {
        if (Files.notExists(file)) {
            return List.of();
        }
        List<Booking> bookings = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 5) {
                throw new IOException("Invalid booking data in " + file);
            }
            try {
                bookings.add(new Booking(fields[0], fields[1], fields[2],
                        Instant.parse(fields[3]), Instant.parse(fields[4])));
            } catch (RuntimeException ex) {
                throw new IOException("Invalid booking data in " + file, ex);
            }
        }
        return bookings;
    }

    void save(List<Booking> bookings) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "bookings-", ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            for (Booking booking : bookings) {
                lines.add(String.join("\t", booking.reference(), booking.student(), booking.slotId(),
                        booking.start().toString(), booking.end().toString()));
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
