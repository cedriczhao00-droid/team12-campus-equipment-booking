# Campus Equipment Booking and Waitlist System

Software Engineering course project by Team 12 at Macau University of Science and Technology.

## Project status

The prototype lets students book an available slot, cancel before it starts, or join a FIFO waiting list. After a cancellation, the first eligible student gets an offer. The offer lasts up to 30 minutes, or until the slot starts if that is sooner. If the student declines or the offer expires, it goes to the next eligible student. Bookings, queue order and offers are saved on disk and remain available after the server restarts.

## Run the prototype

Use Java 17 or later. From the repository root in PowerShell:

```powershell
$sources = Get-ChildItem -LiteralPath src/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d build $sources
java -cp build campus.Main
```

Open <http://127.0.0.1:8080> and book a future slot as Student A. Switch to Students B and C and join the waiting list for the same slot. Switch back to A and cancel the booking. B can then see the offer and deadline under **Your waitlist**. If B declines, C receives the next offer. The app runs on localhost and saves demo data in `data/bookings.tsv`. Run one server instance per data file.

Run the booking checks after compiling the application:

```powershell
$tests = Get-ChildItem -LiteralPath test/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -cp build -d build $tests
java -cp build campus.BookingServiceTest
java -cp build campus.WaitlistTest
```

`Main` handles browser requests, `BookingPage` displays the schedule, `BookingService` manages bookings and offers, and `BookingStore` saves the records. The demo uses selectable student profiles. We still plan to add sign-in, organizer controls, handover and a separate notification history.

## The problem

In a group chat, equipment requests and cancellations can get buried. Students may not know which items are free or notice when a slot opens. Organizers have to keep records and contact students themselves.

## What we plan to build

We are building a browser-based system where students can check availability, book a slot and join a waiting list. Organizers will use it to manage the schedule and review booking changes.

When someone cancels, the system offers the slot to the next eligible student. It holds the slot until that student accepts, declines or misses the deadline. If the offer is declined or expires, the system passes it to the next eligible student.

## Planned features

- Student and organizer accounts.
- An equipment catalog showing available booking slots.
- Booking and cancellation with checks to prevent conflicting reservations.
- Waiting lists where students can check their position and respond to offers.
- Notifications within the system and a record of booking changes.

## Initial scope

We start with one campus club or laboratory, track each item separately and use fixed booking slots. The Task 2 requirements describe our borrowing rules. Payments, smart locks, equipment delivery and university account integration are outside the project scope.

## Team

- Zhao Tiancheng: team leader
- Luo Ruijie: member
- Zeng Congming: member

## How we will work

We keep our code on GitHub and work on feature branches. We plan to review changes through pull requests and share weekly progress reports in Feishu, including each member's actual contributions.
