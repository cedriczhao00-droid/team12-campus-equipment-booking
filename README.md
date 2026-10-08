# Campus Equipment Booking and Waitlist System

Software Engineering course project · Team 12 · Macau University of Science and Technology

## Project status

We are preparing the proposal and gathering requirements. This repository describes what we plan to build. We will add code and setup instructions as development progresses.

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

We will start with one campus club or laboratory, track each item separately, and use fixed booking slots. We will confirm the borrowing rules with customers during requirements elicitation. The first version will exclude payments, smart locks, equipment delivery, and university account integration.

## Team

- Zhao Tiancheng: team leader
- Luo Ruijie: member
- Zeng Congming: member

## How we will work

We plan to keep code in GitHub, work on feature branches, and review changes through pull requests. Weekly progress reports will be shared in Feishu. Each report will state what each member actually contributed.
