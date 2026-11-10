package campus;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record Booking(String reference, String student, String slotId, Instant start, Instant end, Instant cancelledAt) {
    Booking(String reference, String student, String slotId, Instant start, Instant end) {
        this(reference, student, slotId, start, end, null);
    }
    boolean activeAt(Instant now) {
        return cancelledAt == null && end.isAfter(now);
    }
}

enum QueueStatus { WAITING, OFFERED, ACCEPTED, EXPIRED, LEFT, INELIGIBLE }

record QueueEntry(String id, String student, String slotId, QueueStatus status, Instant deadline) {
    boolean active() { return status == QueueStatus.WAITING || status == QueueStatus.OFFERED; }
    QueueEntry change(QueueStatus next, Instant expiry) {
        return new QueueEntry(id, student, slotId, next, expiry);
    }
}

enum BookingStatus { AVAILABLE, RESERVED, HELD, CLOSED, OUTSIDE_WINDOW }

enum BookingResult {
    CONFIRMED("Booking confirmed."),
    ALREADY_CONFIRMED("You already booked this slot."),
    RESERVED("Someone else reserved that slot. Choose another time."),
    HELD("This slot is held for a student on the waitlist."),
    CLOSED("This slot has already started. Choose a later time."),
    OUTSIDE_WINDOW("This slot is outside the 14-day booking window."),
    OVERLAP("You already have an allocation that overlaps this time."),
    LIMIT("You already have three active allocations."),
    INVALID_SLOT("This slot does not exist. Refresh the schedule."),
    CANCELLED("Booking cancelled."),
    JOINED("You joined the waitlist."),
    ALREADY_QUEUED("You are already on this waitlist."),
    QUEUE_LIMIT("You already have five active waitlist entries."),
    OPEN_SLOT("This slot is available. Book it directly."),
    LEFT("You left the waitlist."),
    OFFER_EXPIRED("This offer has expired."),
    INVALID_ACTION("This record is no longer available for that action.");

    final String message;

    BookingResult(String message) {
        this.message = message;
    }
}

final class BookingService {
    private static final int BOOKING_WINDOW_DAYS = 14;
    private static final int MAX_ACTIVE_BOOKINGS = 3;
    private static final int MAX_QUEUE_ENTRIES = 5;
    private static final Duration OFFER_WINDOW = Duration.ofMinutes(30);

    private final Clock clock;
    private final DemoSchedule schedule;
    private final BookingStore store;
    private List<Booking> bookings;
    private List<QueueEntry> queue;

    BookingService(Clock clock, DemoSchedule schedule, BookingStore store) throws IOException {
        this.clock = clock;
        this.schedule = schedule;
        this.store = store;
        var saved = store.loadState();
        bookings = saved.bookings();
        queue = saved.queue();
        refreshOffers();
    }

    synchronized BookingStatus status(Slot slot) {
        return statusAt(slot, clock.instant());
    }

    Instant now() { return clock.instant(); }

    synchronized List<Booking> activeBookingsFor(String student) {
        Instant now = clock.instant();
        return bookings.stream().filter(b -> b.student().equals(student) && b.activeAt(now)).toList();
    }

    synchronized List<QueueEntry> entriesFor(String student) {
        return queue.stream().filter(e -> e.student().equals(student) && e.active()).toList();
    }

    synchronized int position(QueueEntry entry) {
        int position = 0;
        for (QueueEntry item : queue) {
            if (item.active() && item.slotId().equals(entry.slotId())) {
                position++;
                if (item.id().equals(entry.id())) return position;
            }
        }
        return 0;
    }

    synchronized BookingResult book(String student, String slotId) throws IOException {
        Instant now = clock.instant();
        refreshOffers(now);
        Slot slot = schedule.findSlot(slotId);
        if (slot == null) return BookingResult.INVALID_SLOT;
        switch (statusAt(slot, now)) {
            case CLOSED: return BookingResult.CLOSED;
            case OUTSIDE_WINDOW: return BookingResult.OUTSIDE_WINDOW;
            case HELD: return BookingResult.HELD;
            case RESERVED:
                return bookings.stream().anyMatch(b -> b.slotId().equals(slotId)
                        && b.student().equals(student) && b.activeAt(now))
                        ? BookingResult.ALREADY_CONFIRMED : BookingResult.RESERVED;
            case AVAILABLE: break;
        }
        BookingResult rejection = eligibility(student, slot, null, now, bookings, queue);
        if (rejection != null) return rejection;
        List<Booking> updated = new ArrayList<>(bookings);
        updated.add(newBooking(student, slot));
        save(updated, queue);
        return BookingResult.CONFIRMED;
    }

    synchronized BookingResult cancel(String student, String reference) throws IOException {
        Instant now = clock.instant();
        refreshOffers(now);
        List<Booking> updated = new ArrayList<>(bookings);
        for (int i = 0; i < updated.size(); i++) {
            Booking booking = updated.get(i);
            if (!booking.reference().equals(reference) || !booking.student().equals(student)) continue;
            if (booking.cancelledAt() != null) return BookingResult.CANCELLED;
            if (!booking.start().isAfter(now)) return BookingResult.CLOSED;
            updated.set(i, new Booking(booking.reference(), student, booking.slotId(),
                    booking.start(), booking.end(), now));
            List<QueueEntry> nextQueue = new ArrayList<>(queue);
            promote(schedule.findSlot(booking.slotId()), now, updated, nextQueue);
            save(updated, nextQueue);
            return BookingResult.CANCELLED;
        }
        return BookingResult.INVALID_ACTION;
    }

    synchronized BookingResult join(String student, String slotId) throws IOException {
        Instant now = clock.instant();
        refreshOffers(now);
        Slot slot = schedule.findSlot(slotId);
        if (slot == null) return BookingResult.INVALID_SLOT;
        if (entriesFor(student).stream().anyMatch(e -> e.slotId().equals(slotId))) return BookingResult.ALREADY_QUEUED;
        BookingStatus status = statusAt(slot, now);
        if (status == BookingStatus.CLOSED) return BookingResult.CLOSED;
        if (status == BookingStatus.OUTSIDE_WINDOW) return BookingResult.OUTSIDE_WINDOW;
        if (status == BookingStatus.AVAILABLE) return BookingResult.OPEN_SLOT;
        if (bookings.stream().anyMatch(b -> b.student().equals(student) && b.slotId().equals(slotId)
                && b.activeAt(now))) return BookingResult.ALREADY_CONFIRMED;
        if (entriesFor(student).size() >= MAX_QUEUE_ENTRIES) return BookingResult.QUEUE_LIMIT;
        List<QueueEntry> updated = new ArrayList<>(queue);
        updated.add(new QueueEntry(UUID.randomUUID().toString(), student, slotId, QueueStatus.WAITING, null));
        save(bookings, updated);
        return BookingResult.JOINED;
    }

    synchronized BookingResult resolve(String student, String id, boolean accept) throws IOException {
        Instant now = clock.instant();
        refreshOffers(now);
        List<QueueEntry> updated = new ArrayList<>(queue);
        for (int i = 0; i < updated.size(); i++) {
            QueueEntry entry = updated.get(i);
            if (!entry.id().equals(id) || !entry.student().equals(student)) continue;
            if (entry.status() == QueueStatus.EXPIRED) return BookingResult.OFFER_EXPIRED;
            if (accept && entry.status() == QueueStatus.ACCEPTED) return BookingResult.ALREADY_CONFIRMED;
            if (!accept && entry.status() == QueueStatus.LEFT) return BookingResult.LEFT;
            if (!entry.active() || (accept && entry.status() != QueueStatus.OFFERED)) return BookingResult.INVALID_ACTION;
            Slot slot = schedule.findSlot(entry.slotId());
            if (accept) {
                BookingResult rejection = eligibility(student, slot, entry.id(), now, bookings, queue);
                if (rejection == null) {
                    List<Booking> nextBookings = new ArrayList<>(bookings);
                    nextBookings.add(newBooking(student, slot));
                    updated.set(i, entry.change(QueueStatus.ACCEPTED, entry.deadline()));
                    save(nextBookings, updated);
                    return BookingResult.CONFIRMED;
                }
                updated.set(i, entry.change(QueueStatus.INELIGIBLE, entry.deadline()));
                promote(slot, now, bookings, updated);
                save(bookings, updated);
                return rejection;
            }
            updated.set(i, entry.change(QueueStatus.LEFT, entry.deadline()));
            if (entry.status() == QueueStatus.OFFERED) promote(slot, now, bookings, updated);
            save(bookings, updated);
            return BookingResult.LEFT;
        }
        return BookingResult.INVALID_ACTION;
    }

    synchronized void refreshOffers() throws IOException {
        refreshOffers(clock.instant());
    }

    private void refreshOffers(Instant now) throws IOException {
        List<QueueEntry> updated = new ArrayList<>(queue);
        for (int i = 0; i < updated.size(); i++) {
            QueueEntry entry = updated.get(i);
            if (!entry.active()) continue;
            Slot slot = schedule.findSlot(entry.slotId());
            if (slot == null || !slot.start().isAfter(now)
                    || (entry.status() == QueueStatus.OFFERED && !entry.deadline().isAfter(now))) {
                updated.set(i, entry.change(QueueStatus.EXPIRED, entry.deadline()));
                if (entry.status() == QueueStatus.OFFERED) promote(slot, now, bookings, updated);
            }
        }
        if (!updated.equals(queue)) save(bookings, updated);
    }

    // The persisted append order is FIFO, including requests arriving in the same clock tick.
    private void promote(Slot slot, Instant now, List<Booking> nextBookings, List<QueueEntry> nextQueue) {
        if (slot == null || !slot.start().isAfter(now)) return;
        for (int i = 0; i < nextQueue.size(); i++) {
            QueueEntry candidate = nextQueue.get(i);
            if (candidate.status() != QueueStatus.WAITING || !candidate.slotId().equals(slot.id())) continue;
            if (eligibility(candidate.student(), slot, null, now, nextBookings, nextQueue) != null) {
                nextQueue.set(i, candidate.change(QueueStatus.INELIGIBLE, null));
                continue;
            }
            Instant deadline = now.plus(OFFER_WINDOW);
            if (slot.start().isBefore(deadline)) deadline = slot.start();
            nextQueue.set(i, candidate.change(QueueStatus.OFFERED, deadline));
            return;
        }
    }

    private BookingResult eligibility(String student, Slot slot, String ignoredOffer, Instant now,
                                      List<Booking> nextBookings, List<QueueEntry> nextQueue) {
        int active = 0;
        for (Booking booking : nextBookings) {
            if (!booking.student().equals(student) || !booking.activeAt(now)) continue;
            if (overlaps(slot, booking.start(), booking.end())) return BookingResult.OVERLAP;
            active++;
        }
        for (QueueEntry entry : nextQueue) {
            if (!entry.student().equals(student) || entry.id().equals(ignoredOffer)
                    || entry.status() != QueueStatus.OFFERED || !entry.deadline().isAfter(now)) continue;
            Slot held = schedule.findSlot(entry.slotId());
            if (held != null && overlaps(slot, held.start(), held.end())) return BookingResult.OVERLAP;
            active++;
        }
        return active >= MAX_ACTIVE_BOOKINGS ? BookingResult.LIMIT : null;
    }

    private boolean overlaps(Slot slot, Instant start, Instant end) {
        return start.isBefore(slot.end()) && slot.start().isBefore(end);
    }

    private Booking newBooking(String student, Slot slot) {
        return new Booking(UUID.randomUUID().toString(), student, slot.id(), slot.start(), slot.end());
    }

    private void save(List<Booking> nextBookings, List<QueueEntry> nextQueue) throws IOException {
        store.save(nextBookings, nextQueue);
        bookings = List.copyOf(nextBookings);
        queue = List.copyOf(nextQueue);
    }

    private BookingStatus statusAt(Slot slot, Instant now) {
        if (!slot.start().isAfter(now)) return BookingStatus.CLOSED;
        Instant openingLimit = ZonedDateTime.ofInstant(now, DemoSchedule.ZONE)
                .plusDays(BOOKING_WINDOW_DAYS).toInstant();
        if (slot.start().isAfter(openingLimit)) return BookingStatus.OUTSIDE_WINDOW;
        if (bookings.stream().anyMatch(b -> b.slotId().equals(slot.id()) && b.activeAt(now))) {
            return BookingStatus.RESERVED;
        }
        if (queue.stream().anyMatch(e -> e.slotId().equals(slot.id()) && e.status() == QueueStatus.OFFERED)) {
            return BookingStatus.HELD;
        }
        return BookingStatus.AVAILABLE;
    }
}
