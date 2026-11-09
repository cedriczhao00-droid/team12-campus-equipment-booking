package campus;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record Booking(String reference, String student, String slotId, Instant start, Instant end) {}

enum BookingStatus {
    AVAILABLE, RESERVED, CLOSED, OUTSIDE_WINDOW
}

enum BookingResult {
    CONFIRMED("Booking confirmed."),
    ALREADY_CONFIRMED("You already booked this slot."),
    RESERVED("Someone else reserved that slot. Choose another time."),
    CLOSED("This slot has already started. Choose a later time."),
    OUTSIDE_WINDOW("This slot is outside the 14-day booking window."),
    OVERLAP("You already have a booking that overlaps this time."),
    LIMIT("You already have three active bookings."),
    INVALID_SLOT("This slot does not exist. Refresh the schedule.");

    final String message;

    BookingResult(String message) {
        this.message = message;
    }
}

final class BookingService {
    private static final int BOOKING_WINDOW_DAYS = 14;
    private static final int MAX_ACTIVE_BOOKINGS = 3;

    private final Clock clock;
    private final DemoSchedule schedule;
    private final BookingStore store;
    private List<Booking> bookings;

    BookingService(Clock clock, DemoSchedule schedule, BookingStore store) throws IOException {
        this.clock = clock;
        this.schedule = schedule;
        this.store = store;
        bookings = store.load();
    }

    synchronized BookingStatus status(Slot slot) {
        return statusAt(slot, clock.instant());
    }

    synchronized List<Booking> activeBookingsFor(String student) {
        Instant now = clock.instant();
        return bookings.stream()
                .filter(booking -> booking.student().equals(student) && booking.end().isAfter(now))
                .toList();
    }

    // Keep validation and the saved allocation inside the same lock.
    synchronized BookingResult book(String student, String slotId) throws IOException {
        Slot slot = schedule.findSlot(slotId);
        if (slot == null) {
            return BookingResult.INVALID_SLOT;
        }

        Instant now = clock.instant();
        BookingStatus status = statusAt(slot, now);
        switch (status) {
            case CLOSED: return BookingResult.CLOSED;
            case OUTSIDE_WINDOW: return BookingResult.OUTSIDE_WINDOW;
            case RESERVED:
                return bookings.stream().anyMatch(b -> b.slotId().equals(slotId) && b.student().equals(student))
                        ? BookingResult.ALREADY_CONFIRMED : BookingResult.RESERVED;
            case AVAILABLE: break;
        }

        List<Booking> active = bookings.stream()
                .filter(booking -> booking.student().equals(student) && booking.end().isAfter(now))
                .toList();
        for (Booking booking : active) {
            if (booking.start().isBefore(slot.end()) && slot.start().isBefore(booking.end())) {
                return BookingResult.OVERLAP;
            }
        }
        if (active.size() >= MAX_ACTIVE_BOOKINGS) {
            return BookingResult.LIMIT;
        }

        List<Booking> updated = new ArrayList<>(bookings);
        updated.add(new Booking(UUID.randomUUID().toString(), student, slotId, slot.start(), slot.end()));
        store.save(updated);
        bookings = updated;
        return BookingResult.CONFIRMED;
    }

    private BookingStatus statusAt(Slot slot, Instant now) {
        if (!slot.start().isAfter(now)) {
            return BookingStatus.CLOSED;
        }
        Instant openingLimit = ZonedDateTime.ofInstant(now, DemoSchedule.ZONE)
                .plusDays(BOOKING_WINDOW_DAYS).toInstant();
        if (slot.start().isAfter(openingLimit)) {
            return BookingStatus.OUTSIDE_WINDOW;
        }
        for (Booking booking : bookings) {
            if (booking.slotId().equals(slot.id())) {
                return BookingStatus.RESERVED;
            }
        }
        return BookingStatus.AVAILABLE;
    }
}
