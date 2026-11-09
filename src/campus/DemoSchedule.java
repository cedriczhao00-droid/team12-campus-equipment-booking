package campus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

record Equipment(String id, String name, String category, String pickupPoint) {}
record Slot(String id, Equipment equipment, Instant start, Instant end) {}

final class DemoSchedule {
    static final ZoneId ZONE = ZoneId.of("Asia/Macau");
    private static final List<Equipment> EQUIPMENT = List.of(
            new Equipment("CAM-01", "Mirrorless camera", "Media", "Media Lab"),
            new Equipment("AUD-01", "Portable audio recorder", "Media", "Media Lab"),
            new Equipment("TRI-01", "Camera tripod", "Accessories", "Media Lab")
    );
    private static final List<LocalTime> START_TIMES = List.of(
            LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(16, 0)
    );

    List<Equipment> equipment() {
        return EQUIPMENT;
    }

    List<Slot> slotsFor(Equipment item, LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        for (LocalTime start : START_TIMES) {
            String id = item.id() + "/" + date + "/" + start;
            slots.add(new Slot(id, item, date.atTime(start).atZone(ZONE).toInstant(),
                    date.atTime(start.plusHours(2)).atZone(ZONE).toInstant()));
        }
        return slots;
    }

    Slot findSlot(String id) {
        if (id == null || id.length() > 40) {
            return null;
        }
        String[] parts = id.split("/", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(parts[1]);
            for (Equipment item : EQUIPMENT) {
                if (item.id().equals(parts[0])) {
                    return slotsFor(item, date).stream().filter(slot -> slot.id().equals(id)).findFirst().orElse(null);
                }
            }
        } catch (DateTimeParseException ignored) {
            return null;
        }
        return null;
    }
}
