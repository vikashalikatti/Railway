# Seat allocation, waitlist and DB locking

Backend v1.2.0. MySQL 8 (uses `SKIP LOCKED`).

## 1. Coaches and seats

The admin picks coaches per class (`POST /admin/addCoaches/{trainNo}`), for example
`{"SLEEPER_CLASS": 8, "AC3_TIER": 3, "AC2_TIER": 1, "SECOND_CLASS": 2}`. Every seat is created
as a row in `coach_seat`, with its number and berth type (`CoachLayout`):

| Class | Coach | Seats | Berth pattern |
|---|---|---|---|
| 1A | H1, H2… | 18 | L U L U … |
| 2A | A1… | 48 | bays of 6: L U L U SL SU |
| 3A | B1… | 72 | bays of 8: L M U L M U SL SU |
| SL | S1… | 72 | bays of 8: L M U L M U SL SU |
| 2S | D1… | 108 | rows of 6: W M A A M W |

A train can have 1 to 24 coaches. Coaches can't be changed once the train has bookings.

## 2. Per-segment occupancy

Stops are numbered along the route (0 = first station). A taken seat is a row in
`seat_occupancy (seat_id, journey_date, from_stop, to_stop, passenger_id)`.

A seat is free for my journey `[from, to)` when no occupancy row for that seat and date overlaps it:

```sql
NOT EXISTS (SELECT 1 FROM seat_occupancy o
            WHERE o.seat_id = cs.id AND o.journey_date = :date
              AND o.from_stop < :toStop AND o.to_stop > :fromStop)
```

So a seat booked Mysuru(0) → Bengaluru(1) can be sold again for Bengaluru(1) → Hubballi(4).

## 3. Booking: one transaction (`BookingEngine.book`)

`@Transactional(isolation = READ_COMMITTED, timeout = 15)`

1. **Pick a coach** (no lock): count free seats per coach and choose the one with the most free
   seats that fits the whole group, so families sit together and coaches fill evenly.
2. **Lock seats**, three passes:
   - Pass 1: that coach, `wanted + 4` seats (the 4 extra let the seat picker honour berth preferences):
     ```sql
     SELECT cs.* FROM coach_seat cs
     WHERE cs.coach_id = :coachId AND cs.id NOT IN (:exclude) AND <free for segment>
     ORDER BY cs.seat_number LIMIT :lim
     FOR UPDATE SKIP LOCKED
     ```
   - Pass 2: any coach of the class, same `FOR UPDATE OF cs SKIP LOCKED`.
   - Pass 3: only if free seats still exist but are all locked by other in-flight bookings: the same
     query **without** `SKIP LOCKED`, so we wait a moment instead of wrongly waitlisting someone.
3. **Re-check** the locked seats against the latest committed bookings (`findTaken`). Another booking
   may have committed between our search and our lock; those seats are dropped.
4. **Weights** (`SeatPicker`): each passenger/seat pair is scored, and the most demanding passengers pick first:
   - +10 for the requested berth type
   - +8 if the passenger is 60+ and the berth is lower, −6 if it's upper
   - +6 for the same coach as the group's first seat
   - +4 for the same bay, −1 per bay further away
5. **Waitlist** whoever didn't get a seat: lock the `waitlist_counter` row (`SELECT … FOR UPDATE`) and
   hand out WL numbers. The waitlist is capped at 100 per train, date and class; beyond that, `REGRET`.
6. Save the PNR, the passengers and the occupancy rows, then commit. The locks are released at commit.

### Why `SKIP LOCKED`

Two people booking SL on the same train at the same moment both run the pass-1 query. With a plain
`FOR UPDATE`, the second waits until the first commits. With `SKIP LOCKED`, it immediately takes the
next free seats. No queue, and no double booking, because a seat can only be given out by the
transaction holding its row lock.

### Why lock, then re-check

The `NOT EXISTS` filter reads a snapshot taken when the statement started. If a booking for the same seat
commits during that statement, the snapshot misses it. The re-check runs after we hold the lock, and
nobody can add a booking for a seat without that lock, so the re-check is reliable.

### Lock order (deadlock prevention)

Always: **seats → waitlist counter → waitlisted passengers**. Cancellation locks its seats with a
plain `FOR UPDATE ... ORDER BY id`. If MySQL still reports a deadlock or lock timeout, `BookingService`
retries the whole transaction up to 3 times, then answers `409 Try again`.

### Safety nets

- `seat_occupancy` unique keys `(seat_id, journey_date, from_stop)` and `(seat_id, journey_date, to_stop)`
  reject two bookings of a seat that start, or end, at the same stop.
- Overlaps with a different start *and* end can't be expressed as a unique key. Those are prevented
  by the seat row lock alone, which every code path that writes `seat_occupancy` takes first.

## 4. Cancellation and promotion (`BookingEngine.cancel`)

1. Lock the cancelled passengers' seats (`FOR UPDATE`, waits), then the waitlist counter.
2. Delete their occupancy rows and mark them `CAN`.
3. Lock the class's waitlisted passengers (`FOR UPDATE`, oldest WL first). For each one, try to lock a free
   seat for *their* segment (`SKIP LOCKED` only, never waiting) and confirm them.
4. Commit. Bookings that got promoted change status, for example `WAITLISTED` → `CONFIRMED`.

`WL 7` keeps its original number. The PNR status also shows the current position, e.g. `WL 4`.

## 5. API

| Method | Path | What |
|---|---|---|
| GET | `/customer/select_class/{train}/{from}/{to}?seat_type=&date=M/d/yyyy&quota=GN` | `{available, waitlisted, fare, status, berthPreferences}` |
| POST | `/customer/booking/{train}?seat_type=&book_date=&from=&to=&quota=GN` | body `[{"name","age","gender","berthPreference"}]`, 1 to 6 passengers |
| GET | `/admin/quotas/{train}` | quota split per class: `[{seatType, tatkalPct, premiumPct, ladiesPct, seniorPct, saved, seats, seatsByQuota}]` |
| POST | `/admin/quotas/{train}` | body `{"SLEEPER_CLASS": {"tatkalPct":30,"premiumPct":10,"ladiesPct":5,"seniorPct":8}}` |
| GET | `/customer/pnr/{pnr}` | one booking, with current WL positions |
| GET | `/customer/my_bookings` | all of the passenger's bookings |
| POST | `/customer/cancel/{pnr}` | body `[passengerId, …]`, or empty to cancel all |
| GET | `/customer/stations?q=` | live suggestions: cities, stations (code/name/city), legacy stop names; no login |
| GET | `/customer/trains?from=&to=&date=yyyy-MM-dd` | trains between two places; `from`/`to` may be a code, city or name |
| GET | `/admin/trains` | every train with schedule, stops, route and coaches |
| GET | `/admin/distances?codes=SBC,TK,DVG,UBL` | estimated km per leg `[null, 81, 223, 161]`; null where a station has no map position |
| POST | `/admin/stations` | body `{"code","name","city","state"}` adds a station to the directory |
| POST | `/admin/addCoaches/{train}` | body `{"SLEEPER_CLASS": 8, …}` |

All need the `Bearer` header.

## 6. Seeing the locks yourself (two MySQL Workbench tabs)

Tab A:
```sql
START TRANSACTION;
SELECT id, seat_number FROM coach_seat WHERE coach_id = 1 ORDER BY seat_number LIMIT 2 FOR UPDATE SKIP LOCKED;
-- seats 1 and 2 are now locked by tab A
```
Tab B:
```sql
START TRANSACTION;
SELECT id, seat_number FROM coach_seat WHERE coach_id = 1 ORDER BY seat_number LIMIT 2 FOR UPDATE SKIP LOCKED;
-- returns seats 3 and 4 immediately: 1 and 2 are skipped
SELECT id, seat_number FROM coach_seat WHERE coach_id = 1 ORDER BY seat_number LIMIT 2 FOR UPDATE;
-- without SKIP LOCKED this one WAITS until tab A runs COMMIT or ROLLBACK
```
Run `ROLLBACK;` in both tabs when done.

## 7. Upgrading an existing database

`spring.jpa.hibernate.ddl-auto=update` creates the new tables (`coach_seat`, `seat_occupancy`,
`passenger`, `pnr_booking`, `waitlist_counter`) automatically.

Trains created with the old "total seats" step have coaches but no seat rows. From backend v1.2.1,
`LegacyCoachUpgrade` creates those seats automatically when the app starts (and renames
`SLEEPER_CLASS1` to `S1`). To change a train's coaches later, use **Change coaches** on the admin page.

The old `seat`, `booking`, `ticket` and join tables are no longer used. You can leave them, or drop them
once you've upgraded:
```sql
ALTER TABLE train DROP FOREIGN KEY <fk name on seat_id>;  -- find it with: SHOW CREATE TABLE train;
ALTER TABLE train DROP COLUMN seat_id;
DROP TABLE IF EXISTS seat_bookings, booking_seats, ticket_seats, ticket, booking, seat;
```

## Station directory (backend v1.3.0)
`station_master` holds code, name, city and state. On first start `StationSeeder` adds a starter
list (Bengaluru, Mysuru, Hubballi, Dharwad, Chennai, Mumbai, Delhi, Howrah and more) and links
existing stops whose name matches a station name exactly (`station.station_code`). Stops added
by the admin carry the code picked in the search box. Search matches a stop by its code, or by
name when it has no code; a city matches every station in it.

## Editing a train (backend v1.4.0)
`POST /admin/schedule`, `/admin/addstations` and `/admin/addRoute` replace what was saved before
instead of adding to it. Stops are reused position by position, extra rows are deleted and the
route's per-stop prices and total km are recomputed. With upcoming (not cancelled) bookings, the
stop list must stay the same stations in the same order, and a running day can't be dropped if a
booking falls on it.

## Classes
| Class | Code | Prefix | Per coach | Layout |
|---|---|---|---|---|
| AC first class | 1A | H | 18 | cabins |
| Executive chair car | EC | E | 56 | 2+2 |
| AC 2 tier | 2A | A | 48 | 6 per bay |
| AC 3 tier | 3A | B | 72 | 8 per bay |
| AC 3 economy | 3E | M | 81 | 9 per bay (3 side berths) |
| AC chair car | CC | C | 78 | 3+2 |
| Sleeper | SL | S | 72 | 8 per bay |
| Second sitting | 2S | D | 108 | 3+3 |

General (unreserved) coaches aren't included: they have no seat numbers and aren't booked online.

## Quotas (backend v1.6.0)
Every seat has a quota (`coach_seat.quota`): **GN** General, **TQ** Tatkal, **PT** Premium Tatkal,
**LD** Ladies, **SS** Senior citizen. Until the first chart a seat is only sold in its own quota, so
General can be on the waitlist while Tatkal seats are still unsold.

| Quota | Who | Opens | Waitlist | Per PNR |
|---|---|---|---|---|
| GN | anyone | with advance booking | WL | 6 |
| TQ | anyone | day before, 10:00 AC / 11:00 SL and 2S | TQWL | 4 |
| PT | anyone | with Tatkal; fare rises as seats sell (up to 2x) | none | 4 |
| LD | women, and children under 12 with them | with advance booking | none | 6 |
| SS | men 60+, women 45+; lower berths | with advance booking | none | 2 |

- No Tatkal in 1A. Senior citizen only in berth classes (SL, 3A, 2A, 1A, 3E).
- Tatkal charge: 10% of the fare in 2S (min 10, max 15), 30% elsewhere (SL 100–200, CC 125–225,
  3A/3E 300–400, 2A/EC 400–500). Premium Tatkal = (fare + Tatkal charge) x (1 + share of PT seats sold).
- The admin sets each class's split in percent (`quota_share`); General gets the rest. Defaults:
  Tatkal 30, Premium Tatkal 10, Ladies 5, Senior 8 (chair cars and 2S: no Senior; 1A: no Tatkal).
  Seats are marked by `QuotaPlanner`: Senior gets lower berths spread across coaches, Ladies a block
  at the front of the first coach, Premium Tatkal and Tatkal from the last coaches back.
- Each quota with a waitlist has its own counter row (`waitlist_counter.seat_type` is the class for
  General, e.g. `SLEEPER_CLASS`, and `TQ:SL` for Tatkal). A cancelled seat goes to its own quota's waitlist.

## Charts
`ChartScheduler` runs every minute (`chart_run` keeps one row per train and date, locked while a chart runs):
- **First chart**, 4 hours before the train leaves its first station: unsold seats of every quota are
  released; General waitlist, then Tatkal waitlist, get them in WL order; anyone still waiting is
  cancelled (`passenger.cancel_reason = CHART`); everyone is emailed their final status.
  After it, only General booking is open, for seats still free, with no waitlist.
- **Final chart**, 30 minutes before departure (from the passenger's boarding station): booking closes.

## Emails
`TicketMailer` sends an e-ticket (`templates/ticket.ftl`) when a PNR is booked, when a waitlisted
passenger gets a seat, on cancellation, and when the chart is prepared. The content is read inside the
booking transaction and the email is sent after it commits, in the background, so a rollback never
sends an email and a mail problem never affects a booking.
