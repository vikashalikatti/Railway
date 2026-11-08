# Changelog — Railway backend

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
