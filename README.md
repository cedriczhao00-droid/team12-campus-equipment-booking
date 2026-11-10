# Campus Equipment Booking and Waitlist System

Software Engineering course project · Team 12 · Macau University of Science and Technology

## Project status

The prototype supports direct booking, cancellation before the slot starts, and a FIFO waitlist. Cancellation offers the slot to the first eligible student. The offer lasts up to 30 minutes and ends at the slot start if that comes first. A decline or expiry passes the offer to the next eligible student. Bookings, queue order and offers survive a restart.

## Run the prototype

Use Java 17 or later. From the repository root in PowerShell:

```powershell
$sources = Get-ChildItem -LiteralPath src/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d build $sources
java -cp build campus.Main
```

Open <http://127.0.0.1:8080>. Book a future slot as Student A. Select Students B and C in turn and join the waitlist for that slot. Cancel the booking as A, then select B to see the offer and its deadline under **Your waitlist**. Decline as B and check that C receives the next offer. The app binds to localhost and saves demo data in `data/bookings.tsv`. Run one server instance per data file.

Run the booking checks after compiling the application:

```powershell
$tests = Get-ChildItem -LiteralPath test/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -cp build -d build $tests
java -cp build campus.BookingServiceTest
java -cp build campus.WaitlistTest
```

`Main` handles browser requests, `BookingPage` renders the schedule, `BookingService` manages allocations, and `BookingStore` saves records together. The demo uses selectable student profiles. Real sign-in, organizer controls, handover and a separate notification history are planned for later milestones.

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
