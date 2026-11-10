# Campus Equipment Booking and Waitlist System

Software Engineering course project by Team 12 at Macau University of Science and Technology.

## Why we built it

In a group chat, equipment requests and cancellations can get buried. Students may miss an opening, and organizers have to keep a separate record and contact students themselves.

## What works now

Students can check the schedule, book a slot and cancel before it starts. They can join the waitlist if a slot is reserved. When someone cancels, the first eligible student in the queue gets an offer. It lasts up to 30 minutes and ends sooner if the slot starts. If the student declines or the offer expires, the next eligible student gets an offer.

The demo uses three student profiles. It saves bookings, queue entries and offers to `data/bookings.tsv`, so they survive a restart. Run one server per data file.

## Run the prototype

Use Java 17 or later. From the repository root in PowerShell:

```powershell
$sources = Get-ChildItem -LiteralPath src/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d build $sources
java -cp build campus.Main
```

1. Open <http://127.0.0.1:8080> and book a future slot as Student A.
2. Select Student B and click **View schedule**, then join the waitlist for that slot. Repeat as Student C.
3. Switch back to A, click **View schedule**, and cancel the booking.
4. Load B's schedule and look under **Your waitlist** for the offer and deadline. Decline the offer, then load C's schedule to see the next offer. C can accept it to book the slot.

Run the booking checks after compiling the application:

```powershell
$tests = Get-ChildItem -LiteralPath test/campus -Filter '*.java' | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -cp build -d build $tests
java -cp build campus.BookingServiceTest
java -cp build campus.WaitlistTest
```

## Project files

`Main` handles browser requests. `BookingPage` displays the schedule from `DemoSchedule`. `BookingService` applies the booking and waitlist rules, and `BookingStore` saves the records.

## Project scope

We start with one campus club or lab. Each item has its own fixed booking slots, and students can see their place on a waitlist. The Task 2 requirements describe the borrowing rules. We still plan to add sign-in for students and organizers, tools to manage the schedule and handover, in-app notices and a history of booking changes. Payments, smart locks, delivery and university account integration are outside the project scope.

## Team

- Zhao Tiancheng: team leader
- Luo Ruijie: member
- Zeng Congming: member

## How we will work

We keep our code on GitHub and work on feature branches. We plan to review changes through pull requests and share weekly progress reports in Feishu, including each member's actual contributions.
