# Changelog — Railway backend

## v1.13.1 — 10 Nov 2026
- README: step-by-step instructions for running the test cases from PowerShell, Spring Tool Suite
  or Docker, and opening the test records. No code changes.

## v1.13.0 — 10 Nov 2026
Test cases with records. Use with frontend v1.17.0. No database changes.

- **746 recorded test cases** in 13 `*CasesTest` classes: fares, Tatkal and Premium Tatkal,
  refunds, RAC and the waitlist chance, quotas and how seats are split, seat allocation, solo
  women / seniors / helpers, timetables and day numbers, payments, rate limits, sign-up checks,
  profile photos, log-in lockouts, and the train importer with the Karnataka list.
- **Test records**: every case keeps its input values, expected and actual result, PASS / FAIL and
  why it failed (with the test file and line). Written to `target/test-records/test-records.html`
  (filter "only failed") and `test-records.csv` (Excel). One failing case doesn't stop the others.
- **Fix found by the tests**: booking in the RAC-berth quota said "RAC berth allows up to 0
  passengers per booking" instead of explaining that RAC berths can't be booked directly.
- Run with `mvnw test -Dtest="*RulesTest,TrainImportTest,*CasesTest"`; the Docker build runs them too.

## v1.12.0 — 9 Nov 2026
Karnataka trains, Docker and Kubernetes. Use with frontend v1.17.0. No database changes.

- **Karnataka train list** replaces the all-India list in the admin's import: 438 trains and 430
  stations (418 in Karnataka, found with Karnataka's state boundary; the rest are the far ends of
  Vande Bharat routes, such as Chennai, Madgaon and Pune).
  - Every train with at least two stops in Karnataka, cut to its stops inside the state, with km
    and day numbers starting again from its first Karnataka stop.
  - Passenger, MEMU and DEMU trains are included with 4 second-sitting (2S) coaches.
  - All 12 Vande Bharat trains serving Karnataka, from public timetables: 20661/62, 26751/52,
    20607/08, 22231/32, 20645/46, 20669/70, with their running days, full routes and chair car
    (CC) + executive (EC) coaches.
  - Big stations use today's names (KSR Bengaluru, Mysuru, Hubballi, Belagavi, Kalaburagi with code
    KLBG, SMVT Bengaluru SMVB is new).
  - Every station in the list joins the station directory on import, even one no imported train
    stops at.
  - Bundled lists can now give each train's running days and coaches.
  - `tools/build-karnataka-railways.py` rebuilds the list. `tools/build-indian-railways.py` stays
    for anyone who wants all of India again.
- **Docker**: `Dockerfile` (builds the jar and runs the unit tests inside Docker; runs as a
  non-root user; health check), `docker-compose.yml` (MySQL 8.4 + API + web app) and `.env.example`.
- **Kubernetes** (`k8s/`): MySQL StatefulSet with its own disk, backend and web app Deployments
  with startup / readiness / liveness checks, rolling updates and CPU autoscalers, a ConfigMap and
  a Secret template. `kubectl apply -k k8s/`.
- **docs/DEPLOYMENT.md**: installing Docker Desktop and Kubernetes on Windows, running with Docker
  Compose, deploying, scaling, updating and troubleshooting on Kubernetes.
- `TRUST_PROXY=true`: behind nginx / Kubernetes, rate limits use the client address from
  `X-Forwarded-For` when the request comes from a private network address. The last address in the
  header is used now (the one our own proxy wrote), also for the Vite dev proxy.
- Health checks: `/actuator/health/liveness` and `/actuator/health/readiness` are open.

## v1.11.1 — 9 Nov 2026
- README: what the project does, setup and environment variables, test payments, how booking works,
  the main endpoints and the project structure. No code changes. Use with frontend v1.16.1.

## v1.11.0 — 9 Nov 2026
RAC, cancellation charges, Day 2/3 stops, Vikalp, Divyangjan quota, test payments, e-tickets, admin
tools and rate limits. Use with frontend v1.16.0. Hibernate adds the new tables (payment,
refund_entry, audit_log) and columns (passenger: rac_number, refund, cancellation_charge, divyang,
concession_card; pnr_booking: boarding_date, payment_id, vikalp, vikalp_from, note; station:
day_number; quota_share: rac_berths, divyang_pct; train_run: late_notified_minutes; customer:
blocked, blocked_reason).

**Fix**: the v1.10.0 train import saved each stop's km from the FIRST station, but the app adds up
km from the PREVIOUS stop, so imported trains' fares were far too high. Delete trains imported with
v1.10.0 and import them again.

- **RAC**: side-lower berths in SL, 3A, 3E and 2A are kept as RAC berths (default 4, 2, 2, 1 per
  coach; the admin's quota editor sets them). When a class is full, General and Tatkal passengers
  get RAC (two per RAC berth, counted per stretch of the route) before the waitlist. A freed berth
  goes to RAC first, then the waitlist moves up into RAC. At the chart, RAC passengers left share
  RAC berths in pairs. Booking status RAC; tickets show "RAC 3" or "RAC S1/39 (shared side lower)".
  Existing trains get RAC berths when their quotas are saved again.
- **Cancellation charges** (Indian Railways rules): confirmed tickets lose a flat charge per class
  more than 48 h before departure, 25% from 48 to 12 h, 50% from 12 h to the chart, and get nothing
  back after the chart; RAC and waitlisted tickets lose a ₹60 clerkage charge; Tatkal confirmed and
  Premium Tatkal get nothing back; meals and bedrolls are refunded before the chart. Passengers
  still waitlisted at the chart are refunded minus clerkage; train cancellations refund everything.
  POST /customer/cancel_preview/{pnr} shows the refund before cancelling.
- **Day 2 / Day 3 stops**: each stop has a day (station.day_number), set by the admin or worked out
  from the times. The search date is the day the passenger boards; seats, charts and runs belong to
  the day the train left its first station (booking.journey_date), and booking.boarding_date is the
  boarding day. Searches, charts, Tatkal, connections and emails use real date-times.
- **Vikalp**: a waitlisted booking can opt in (?vikalp=true). If still fully waitlisted at the
  chart, it moves to another train between the same stations, same class, leaving within 12 hours,
  that has berths for everyone (new PNR, no extra charge); otherwise it's dropped with a refund.
- **Divyangjan quota (PH)**: a passenger with a disability concession card plus one escort, lower
  berths (2% of SL, 3A, 3E, 2A by default). Only the card's last 4 characters are stored.
- **Test payment gateway**: POST /customer/payments, /payments/{id}/confirm (card + OTP 123456,
  UPI, net banking; test failure cases). Bookings need a PAID payment (?payment_id=…; switch off with
  PAYMENTS_REQUIRED=false). Refunds go back to the payment. Unused payments are refunded after
  30 minutes. Card numbers and CVVs are never stored.
- **E-tickets**: GET /customer/ticket-code/{pnr} signs a QR code; GET /customer/verify-ticket
  (public) shows a ticket checker the live status, or that the code is forged or expired.
- **Timetable**: GET /customer/timetable/{train} and /customer/train-search (public).
- **Search**: results carry boarding date, departure / arrival date-times, journey minutes, the day
  of each end, and free General berths per class.
- **Running late emails**: when the admin's live position shows the train 30+ minutes late,
  passengers who haven't boarded yet are emailed, and again each time it grows by 30 minutes.
- **Waitlist chance**: availability and tickets show the chance of confirmation (High / Medium /
  Low with a percentage), from the last 90 days of this train and class, or a rule of thumb.
- **Customers (admin)**: GET /admin/customers, /admin/customers/{id}/bookings, POST /block and
  /unblock. Blocked customers are logged out everywhere and can't log in or book.
- **Audit log (admin)**: GET /admin/audit. Records log-ins, trains, stops, fares, coaches, quotas,
  run cancellations, reschedules, live positions, imports and customer blocks.
- **Rate limits** per client (RATE_LIMIT=false to switch off): log-ins 20/min, sign-up 10/min,
  PNR status 20/min, ticket checks and payments 30/min, timetables and running status 120/min.
  Over the limit: 429 with Retry-After.

## v1.10.0 — 9 Nov 2026
Safer seating, boarding help, and importing trains in bulk. Use with frontend v1.15.0. New columns
on passenger (needs_assistance, helper, companion_id, companion_note, near_women) and an index on
station.station_code are added by Hibernate.
- Solo women: a woman (12+) who is the only passenger with a berth on her ticket is seated, when
  possible, in a bay where other women (18+) are booked for an overlapping stretch. It works inside
  the seat allocation (SeatPicker "near" bonus); nobody is ever shown where women sit. Her ticket
  says "Seated in a bay with other women" when it worked.
- Boarding help: a senior (60+) travelling alone can tick "Needs boarding help"; adults (18-59)
  can volunteer as helpers. Helpers and seniors are seated near each other, and each senior is
  matched with one helper in the same coach who is on the train when the senior boards (at
  booking, when someone cancels, and again at the first chart). Both tickets and emails show the
  match (first name, coach and berth). The admin chart has a "Boarding help" column.
- Bulk import (admin): GET/POST /admin/import/bundled imports the bundled Indian Railways list
  (2,215 trains with reserved classes, 3,964 stations; DataMeet open data, CC0, collected 2016,
  so timings may be out of date and every train runs daily). POST /admin/import/upload takes a CSV
  or JSON file (up to 20 MB). Options: coaches per class, base fare, only trains stopping at some
  stations, at most N trains. GET /admin/import/status shows progress. Trains already in the
  database are skipped, never changed; new station codes are added to the directory. Runs in the
  background; SQL logging is paused while it runs.
- GET /admin/trains?q=&limit= searches on the server (number, name, first or last station) and
  returns at most 200 trains; GET /admin/trains/{no} returns one train.
- Thousands of trains: searches, connections, the chart scheduler and reports no longer load every
  train with every stop. They query only the trains that can match.

## v1.9.0 — 9 Nov 2026
Passenger and admin features. Use with frontend v1.14.0. New tables saved_passenger and train_run,
and new columns on passenger (no_berth, meal, bedroll, extras_fare) and booking (refund_amount),
are added by Hibernate.
- GET /customer/pnr-status/{pnr} (public): train, journey and each passenger's booking and current
  status, numbered 1, 2, 3. No names or ages.
- Saved passengers: GET/POST /customer/saved_passengers, DELETE /customer/saved_passengers/{id}
  (up to 20 per customer; saving the same name again updates it).
- Children: under 5 travel free without a berth (status NB, at most 2 per ticket, don't count
  toward the passenger limit). Ages 5–11 may choose "no berth" for half fare. A ticket needs at
  least one passenger with a berth; if all berth holders are cancelled, the no-berth ones are too.
- Meals (Veg ₹120, Non-veg ₹150, Jain ₹120) and bedroll (₹150 in Sleeper; included in AC berth
  classes) per passenger; stored, charged and shown on the ticket and emails.
- GET /customer/connections?from&to&date: journeys with one change on the same day, 30 min to 8 h
  wait, earliest arrival first (up to 10).
- POST /admin/runs/{train}/cancel {date, reason}: cancels every passenger on that date, frees the
  berths, records a full refund (fare + extras) on each booking and emails everyone. Bookings for a
  cancelled run are refused.
- POST /admin/runs/{train}/reschedule {date, delayMinutes, reason}: emails each passenger the new
  departure time from their boarding station.
- POST /admin/runs/{train}/position {date, stop, departed, delayMinutes} and
  GET /customer/running/{train}?date (public): live running status shown on tickets.
- GET /admin/reports?from&to (up to 92 days): totals, occupancy and revenue per train and class,
  and a day-by-day table of bookings, cancellations and waitlist results.
- Refunds are recorded only; no payment gateway yet.

## v1.8.0 — 9 Nov 2026
Coaches by date (rush days). Use with frontend v1.13.0.
- Coaches have optional "runs from" / "runs until" dates (coach.valid_from, coach.valid_to, added
  by Hibernate). Availability, booking, waitlist promotion and charts only use coaches running on
  the journey date; a class with no coach that day says so.
- POST /admin/coaches/{train}/add {seatType, count, from, to?}: extra coaches from a date, for good
  or until an end date. Allowed whatever is booked. Their seats are General and go straight to the
  class's waitlist on those dates (General first, then Tatkal), and those passengers are emailed.
- POST /admin/coaches/{train}/remove {coachNumbers, from}: takes coaches off from a date on. Only
  confirmed passengers in those coaches on or after that date stop it; cancelled tickets never do.
- "Change coaches" (replace all) is now only blocked by upcoming tickets that aren't cancelled;
  past and cancelled tickets no longer block it.

## v1.7.0 — 9 Nov 2026
Chart & vacancy. Use with frontend v1.11.0.
- GET /customer/chart/{train}?date=yyyy-MM-dd (public, no log-in): once the first chart is prepared,
  every coach's berths and the stretches each is booked for. Never includes names, ages or PNRs.
  Before the chart it answers 409 with the time the chart will be ready.
- GET /admin/chart/{train}?date=yyyy-MM-dd (admin, any time): the same berth map plus every
  passenger: name, age, gender, PNR, class, berth, journey, quota and status (including
  "Not confirmed at charting").
- The chart-prepared email now points passengers to Chart & vacancy.

## v1.6.1 — 9 Nov 2026
- Ticket emails: if the email layout ever fails, a plain-text email is still sent; preparing an
  email can never cancel a booking or a cancellation.
- The Spring console shows every ticket email: "Ticket email queued", "Ticket email sent", or
  "Ticket email ... was NOT sent" with the reason.

## v1.6.0 — 9 Nov 2026
Quotas, Tatkal, charts and ticket emails. Use with frontend v1.10.0. See docs/SEAT-ALLOCATION.md.
- Quotas on every seat: General, Tatkal, Premium Tatkal, Ladies, Senior citizen, each sold only in
  its own quota until the chart. Admin sets the split per class (GET/POST /admin/quotas/{train});
  new coaches get the defaults.
- Tatkal and Premium Tatkal: open the day before (10:00 AC, 11:00 SL/2S), Tatkal charge, Premium
  Tatkal fare rising with sales, max 4 per PNR, no Tatkal in 1A. Tatkal has its own waitlist (TQWL);
  Premium Tatkal, Ladies and Senior citizen have none.
- Ladies (women and children under 12) and Senior citizen (men 60+, women 45+, lower berths, max 2)
  checked on the server.
- Automatic charts: first chart 4 hours before departure releases unsold quota seats to the waitlist
  and cancels whoever is still waiting; final chart 30 minutes before closes booking.
- Ticket emails: booked, waitlist confirmed, cancelled, chart prepared.
- New tables/columns (created by Hibernate): quota_share, chart_run, coach_seat.quota,
  pnr_booking.quota, passenger.cancel_reason.

## v1.5.2 — 9 Nov 2026
- springdoc 2.3.0 for Spring Boot 3.2; Swagger "Authorize" button for the Bearer header.
- Admin login alert email sent in the background; skipped when the admin has no email; no
  OpenCage lookup unless opencage.api-key is set; sent from spring.mail.username.
