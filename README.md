# Campus Equipment Booking and Waitlist System

Software Engineering course project · Team 12 · Macau University of Science and Technology

## Project status

The first runnable milestone covers the equipment catalog, date-based availability, and direct booking. It uses sample equipment and two demo students. A saved booking remains after a restart. The server checks for occupied slots, personal time overlaps, the three-active-booking limit, and the 14-day booking window before it writes a booking.

## Run the first milestone

Use Java 17 or later. From the repository root in PowerShell:

```powershell
$sources = Get-ChildItem -LiteralPath src/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d build $sources
java -cp build campus.Main
```

Open <http://127.0.0.1:8080>. Select a date and demo student, then click **Book slot**. Change to the other demo student and view the same schedule to see the reserved slot. The app binds to localhost and saves demo data in `data/bookings.tsv`. Run only one server instance per data file.

Run the booking checks after compiling the application:

```powershell
javac --release 17 -encoding UTF-8 -cp build -d build test/campus/BookingServiceTest.java
java -cp build campus.BookingServiceTest
```

`Main` handles browser requests, `BookingPage` renders the schedule, `BookingService` checks and commits bookings, and `BookingStore` saves records. Account sign-in, organizer controls, cancellation, waitlist offers and handover are planned for later milestones. In the demo, a booking counts as active until its slot ends.

## The problem

In a group chat, equipment requests and cancellation messages can get buried. Students may struggle to tell whether an item is available or miss a slot they were waiting for. Organizers have to keep a separate record and contact students when bookings change.

## What we plan to build

A browser-based system where students can check equipment availability, book a slot, or join a waiting list. Organizers will manage the schedule and review booking changes in the same service.

When someone cancels, the system will offer the slot to the next eligible student on the waiting list. It will hold the slot until that student accepts, declines, or misses the deadline. A declined or expired offer will pass to the next student.

## Planned features

- Student and organizer accounts.
- An equipment catalog showing available booking slots.
- Booking and cancellation with checks to prevent conflicting reservations.
- Waiting lists where students can check their position and respond to offers.
- Notifications within the system and a record of booking changes.

## Initial scope

We start with one campus club or laboratory, track each item separately, and use fixed booking slots. The Task 2 requirements describe the project's borrowing rules. The project scope excludes payments, smart locks, equipment delivery, and university account integration.

## Team

- Zhao Tiancheng: team leader
- Luo Ruijie: member
- Zeng Congming: member

## How we will work

We plan to keep code in GitHub, work on feature branches, and review changes through pull requests. Weekly progress reports will be shared in Feishu. Each report will state what each member actually contributed.
