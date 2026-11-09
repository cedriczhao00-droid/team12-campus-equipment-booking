package campus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class BookingPage {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(DemoSchedule.ZONE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    static String render(LocalDate date, LocalDate today, String student, BookingResult result,
                         DemoSchedule schedule, BookingService service) {
        StringBuilder page = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Campus Equipment Desk</title><link rel="stylesheet" href="/style.css"></head><body>
                <header><span class="ticket" aria-hidden="true">↗</span> Campus Equipment Desk</header>
                <main><section class="intro"><h1>Book equipment</h1>
                <p>Choose an available time and collect your equipment at the Media Lab.</p></section>
                <form class="filters" action="/" method="get">
                """);
        page.append("<label>Borrowing date<input type='date' name='date' required min='")
                .append(today).append("' max='").append(today.plusDays(14)).append("' value='").append(date)
                .append("'></label><label>Demo student<select name='student'>");
        appendOption(page, student, "student-a", "Student A");
        appendOption(page, student, "student-b", "Student B");
        page.append("</select></label><button class='primary' type='submit'>View schedule</button></form>");

        if (result != null) {
            page.append("<p class='result' role='status'>").append(result.message).append("</p>");
        }
        page.append("<section aria-labelledby='schedule'><div class='schedule-title'><h2 id='schedule'>")
                .append(date.format(DATE)).append("</h2><span>Macao time</span></div>");
        for (Equipment item : schedule.equipment()) {
            page.append("<article class='equipment'><div class='item'><span class='asset'>").append(item.id())
                    .append("</span><h3>").append(item.name()).append("</h3><p>").append(item.category())
                    .append(" / ").append(item.pickupPoint()).append("</p></div><ul class='slots'>");
            for (Slot slot : schedule.slotsFor(item, date)) {
                appendSlot(page, date, student, slot, service.status(slot));
            }
            page.append("</ul></article>");
        }
        page.append("</section><section class='bookings'><h2>Your active bookings</h2>");
        var active = service.activeBookingsFor(student);
        if (active.isEmpty()) {
            page.append("<p>Choose an available slot above to make a booking.</p>");
        } else {
            page.append("<ul>");
            for (Booking booking : active) {
                Slot slot = schedule.findSlot(booking.slotId());
                if (slot != null) {
                    page.append("<li><strong>").append(slot.equipment().name()).append("</strong> · ")
                            .append(booking.start().atZone(DemoSchedule.ZONE).toLocalDate().format(DATE))
                            .append(" · ").append(TIME.format(booking.start())).append("–")
                            .append(TIME.format(booking.end())).append("</li>");
                }
            }
            page.append("</ul>");
        }
        return page.append("</section></main><footer>Campus Equipment Desk · Team 12</footer></body></html>").toString();
    }

    private static void appendOption(StringBuilder page, String student, String value, String label) {
        page.append("<option value='").append(value).append("'");
        if (student.equals(value)) {
            page.append(" selected");
        }
        page.append(">").append(label).append("</option>");
    }

    private static void appendSlot(StringBuilder page, LocalDate date, String student, Slot slot, BookingStatus status) {
        String time = TIME.format(slot.start()) + "–" + TIME.format(slot.end());
        page.append("<li><time>").append(time).append("</time>");
        if (status == BookingStatus.AVAILABLE) {
            page.append("<form action='/book' method='post'><input type='hidden' name='date' value='")
                    .append(date).append("'><input type='hidden' name='student' value='").append(student)
                    .append("'><input type='hidden' name='slot' value='").append(slot.id())
                    .append("'><button type='submit' aria-label='Book ").append(slot.equipment().name())
                    .append(" at ").append(time).append("'>Book slot</button></form>");
        } else {
            String label = switch (status) {
                case RESERVED -> "Reserved";
                case CLOSED -> "Closed";
                case OUTSIDE_WINDOW -> "Opens later";
                default -> throw new IllegalArgumentException("Unexpected slot status");
            };
            page.append("<span class='unavailable'>").append(label).append("</span>");
        }
        page.append("</li>");
    }

    static final String STYLE = """
            :root { color-scheme:light; --ink:#142a3c; --paper:#edf3f5; --line:#bacbd2; --green:#d9efbc; --navy:#12334a; }
            * { box-sizing:border-box; }
            body { margin:0; color:var(--ink); background:var(--paper); font:16px/1.5 'Trebuchet MS',Arial,sans-serif; }
            header,footer { padding:1rem max(1.1rem,calc((100% - 64rem)/2)); }
            header { display:flex; align-items:center; gap:.65rem; background:var(--navy); color:white; font-weight:bold; }
            .ticket { display:grid; place-items:center; width:1.8rem; height:1.8rem; background:var(--green); color:var(--navy); border-radius:.3rem; }
            main { max-width:66rem; padding:1.5rem 1rem 2.5rem; margin:auto; }
            .intro { border-bottom:3px solid var(--navy); padding:.5rem 0 1rem; }
            h1 { font-size:clamp(2.3rem,6vw,4rem); line-height:1; letter-spacing:-.055em; margin:0 0 .6rem; }
            p,h2,h3 { margin:0; } h2 { font-size:1.4rem; } h3 { font-size:1.15rem; }
            .filters { display:flex; flex-wrap:wrap; align-items:end; gap:.75rem; margin:1rem 0 1.5rem; }
            label { display:grid; gap:.25rem; font-weight:bold; font-size:.9rem; }
            input,select,button { font:inherit; padding:.48rem .65rem; color:inherit; border:1px solid var(--line); border-radius:.28rem; background:white; }
            button { font-weight:bold; cursor:pointer; background:var(--green); border-color:#a7c584; white-space:nowrap; }
            button:hover { background:#c8e5a2; } .primary { background:var(--navy); border-color:var(--navy); color:white; }
            .primary:hover { background:#244c66; }
            :focus-visible { outline:3px solid #096bac; outline-offset:2px; }
            .result { border-left:4px solid var(--navy); padding:.55rem .8rem; background:white; margin-bottom:1rem; }
            .schedule-title { display:flex; flex-wrap:wrap; gap:.45rem; align-items:baseline; justify-content:space-between; border-bottom:1px solid var(--line); }
            .equipment { display:grid; grid-template-columns:minmax(11rem,1fr) 3fr; gap:.85rem; padding:1rem 0; border-bottom:1px solid var(--line); }
            .asset,.item p,.schedule-title span { color:#486373; font-size:.82rem; }
            ul.slots { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:.4rem; list-style:none; margin:0; padding:0; }
            .slots li { min-width:0; display:grid; justify-items:start; align-content:start; gap:.25rem; padding:.45rem; background:white; border-top:2px solid #8baaac; }
            time { font-variant-numeric:tabular-nums; font-weight:bold; }
            .unavailable { font-size:.9rem; padding:.4rem 0; color:#486373; }
            .bookings { margin-top:1.3rem; } footer { border-top:1px solid var(--line); color:#486373; font-size:.9rem; }
            @media(max-width:760px) { .equipment { grid-template-columns:1fr; } }
            @media(max-width:540px) { ul.slots { grid-template-columns:repeat(2,minmax(0,1fr)); } }
            """;
}
