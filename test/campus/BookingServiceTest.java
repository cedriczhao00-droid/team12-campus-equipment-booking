package campus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class BookingServiceTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("campus-booking-test-");
        Path file = root.resolve("bookings.tsv");
        DemoSchedule schedule = new DemoSchedule();
        LocalDate date = LocalDate.of(2026, 11, 10);
        Clock clock = Clock.fixed(date.atTime(8, 0).atZone(DemoSchedule.ZONE).toInstant(), DemoSchedule.ZONE);
        BookingStore store = new BookingStore(file);
        try {
            BookingService service = new BookingService(clock, schedule, store);
            List<Slot> camera = schedule.slotsFor(schedule.equipment().get(0), date);
            Slot overlap = schedule.slotsFor(schedule.equipment().get(1), date).get(0);

            raceForSlot(service, camera.get(0));
            String winner = store.load().get(0).student();
            expect(BookingResult.ALREADY_CONFIRMED, service.book(winner, camera.get(0).id()));
            expect(BookingResult.OVERLAP, service.book(winner, overlap.id()));
            expect(BookingResult.CONFIRMED, service.book(winner, camera.get(1).id()));
            expect(BookingResult.CONFIRMED, service.book(winner, camera.get(2).id()));
            expect(BookingResult.LIMIT, service.book(winner, camera.get(3).id()));
            expect(BookingResult.INVALID_SLOT, service.book(winner, "not-a-slot"));

            BookingService restarted = new BookingService(clock, schedule, store);
            expect(BookingStatus.RESERVED, restarted.status(camera.get(0)));
            expect(3, restarted.activeBookingsFor(winner).size());
            expect(0, restarted.activeBookingsFor("other").size());
            expect(BookingResult.OUTSIDE_WINDOW, restarted.book("other",
                    schedule.slotsFor(schedule.equipment().get(0), date.plusDays(14)).get(0).id()));
            Clock atStart = Clock.fixed(camera.get(0).start(), DemoSchedule.ZONE);
            BookingService later = new BookingService(atStart, schedule, store);
            expect(BookingResult.CLOSED, later.book("other", camera.get(0).id()));
            System.out.println("Booking checks passed: concurrent contention, overlap, limit, retry, time window and restart.");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }

    private static void raceForSlot(BookingService service, Slot slot) throws Exception {
        int students = 20;
        var pool = Executors.newFixedThreadPool(students);
        CountDownLatch ready = new CountDownLatch(students);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BookingResult>> requests = new ArrayList<>();
            for (int i = 0; i < students; i++) {
                String student = "test-student-" + i;
                requests.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Race did not start");
                    }
                    return service.book(student, slot.id());
                }));
            }
            expect(true, ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int confirmed = 0;
            for (Future<BookingResult> request : requests) {
                BookingResult result = request.get(5, TimeUnit.SECONDS);
                if (result == BookingResult.CONFIRMED) {
                    confirmed++;
                } else {
                    expect(BookingResult.RESERVED, result);
                }
            }
            expect(1, confirmed);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
