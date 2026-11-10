package campus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class WaitlistTest {
    private static final class TestClock extends Clock {
        Instant time;
        TestClock(Instant time) { this.time = time; }
        public Instant instant() { return time; }
        public ZoneId getZone() { return DemoSchedule.ZONE; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("campus-waitlist-", ".tsv");
        try {
            DemoSchedule schedule = new DemoSchedule();
            Slot slot = schedule.slotsFor(schedule.equipment().get(0), LocalDate.of(2026, 11, 12)).get(0);
            TestClock clock = new TestClock(slot.start().minusSeconds(2400));
            BookingStore store = new BookingStore(file);
            // Start from the previous five-column file format.
            Files.writeString(file, "old-reference\ta\t" + slot.id() + "\t" + slot.start() + "\t" + slot.end() + "\n");
            BookingService service = new BookingService(clock, schedule, store);
            expect(BookingResult.JOINED, service.join("b", slot.id()));
            expect(BookingResult.JOINED, service.join("c", slot.id()));
            expect(BookingResult.ALREADY_QUEUED, service.join("b", slot.id()));
            expect(BookingResult.INVALID_ACTION, service.cancel("b", "old-reference"));
            expect(BookingResult.CANCELLED, service.cancel("a", "old-reference"));
            QueueEntry first = service.entriesFor("b").get(0);
            expect(QueueStatus.OFFERED, first.status());
            expect(2, service.position(service.entriesFor("c").get(0)));
            expect(BookingResult.HELD, service.book("a", slot.id()));
            expect(BookingResult.INVALID_ACTION, service.resolve("c", first.id(), true));

            clock.time = first.deadline();
            service = new BookingService(clock, schedule, store);
            QueueEntry second = service.entriesFor("c").get(0);
            expect(QueueStatus.OFFERED, second.status());
            expect(slot.start(), second.deadline());
            expect(BookingResult.OFFER_EXPIRED, service.resolve("b", first.id(), true));
            expect(BookingResult.CONFIRMED, service.resolve("c", second.id(), true));
            expect(BookingResult.ALREADY_CONFIRMED, service.resolve("c", second.id(), true));
            expect(BookingResult.CANCELLED, service.cancel("a", "old-reference"));
            expect(1, service.activeBookingsFor("c").size());

            String ref = service.activeBookingsFor("c").get(0).reference();
            expect(BookingResult.JOINED, service.join("a", slot.id()));
            expect(BookingResult.JOINED, service.join("b", slot.id()));
            expect(BookingResult.CANCELLED, service.cancel("c", ref));
            expect(BookingResult.LEFT, service.resolve("a", service.entriesFor("a").get(0).id(), false));
            expect(QueueStatus.OFFERED, service.entriesFor("b").get(0).status());
            clock.time = slot.start();
            service.refreshOffers();
            expect(0, service.entriesFor("b").size());
            expect(BookingStatus.CLOSED, service.status(slot));
            System.out.println("Waitlist checks passed: ownership, FIFO, decline, exact expiry, restart and retry.");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
