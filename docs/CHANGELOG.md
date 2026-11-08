# Changelog — Railway backend

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
