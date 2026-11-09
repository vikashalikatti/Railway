# Railway — train ticket booking (backend)

A train reservation system modelled on how Indian Railways booking works: search trains between
stations, book berths with quotas, Tatkal and RAC, a waitlist that moves up automatically,
reservation charts, cancellation charges, e-tickets with a QR code, and an admin console to run it all.

This repository is the **Spring Boot REST API**. The web app that uses it is
[Railway_Frontend](https://github.com/vikashalikatti/Railway_Frontend).

> This is a learning project. Payments go through a built-in **test gateway**: no real money moves.
> It is not affiliated with Indian Railways or IRCTC.

---

## What it does

### For passengers
- **Search trains** between any two stations or cities (station code, name or city), with journey
  times across midnight, Day 2 / Day 3 stops, and free berths per class. If no train runs all the
  way, **connecting journeys** with one change are suggested.
- **Book up to 6 passengers** in 8 classes (1A, 2A, 3A, 3E, SL, CC, EC, 2S) and 6 quotas: General,
  Tatkal, Premium Tatkal, Ladies, Senior citizen and Divyangjan.
- **Real coach layouts** (8-berth bays, 2A/1A cabins, 3+2 and 2+2 chair cars). Seniors get lower
  berths; families sit together; a woman travelling alone is seated near other women where possible;
  seniors travelling alone can ask for a volunteer helper on the same coach.
- **RAC and waitlist**: when a class is full, passengers get RAC (two per side-lower berth), then a
  waitlist number. Cancellations move RAC and the waitlist up automatically, and passengers are emailed.
- **Children**: under 5 travel free without a berth; 5–11 can choose no berth at half fare.
- **Meals and bedroll** per passenger.
- **Vikalp**: a waitlisted ticket can move to another train with berths when the chart is prepared.
- **Pay** through the test gateway (card + OTP, UPI, net banking). Refunds go back automatically.
- **Cancel** passengers with Indian Railways cancellation charges, shown before you confirm.
- **PNR status** (public, no names), **chart & vacancy**, **live running status**, **timetables**,
  **waitlist confirmation chance**, **saved passengers**, and **e-ticket emails** for every change.
- **E-ticket QR codes** that a ticket checker can scan to verify the ticket is genuine.

### For admins
- **Add and edit trains** step by step: details, running days, stops (km estimated from station
  positions, day of each stop), fare, coaches.
- **Import trains in bulk**: the bundled list of **438 trains in Karnataka** (including all 12
  Vande Bharat trains serving the state) and **418 Karnataka stations**, or your own CSV / JSON file.
- **Quotas per class**: Tatkal, Premium Tatkal, Ladies, Senior, Divyangjan shares and RAC berths.
- **Extra coaches on rush days**, or remove coaches from a date.
- **Reservation charts** with every passenger, prepared automatically 4 hours before departure.
- **Train runs**: update live position and delay (passengers are emailed if it's 30+ minutes late),
  reschedule, or cancel a run (everyone refunded and emailed).
- **Reports**: occupancy, revenue, cancellations and waitlist results by train, class and day.
- **Customers**: search, see bookings, block / unblock.
- **Audit log** of every admin change.

---

## Tech stack

| | |
|---|---|
| Language | Java 17+ (tested with 21) |
| Framework | Spring Boot 3.2 (Web, Data JPA, Security, Mail, Validation) |
| Database | MySQL 8 (tables created and updated by Hibernate) |
| Security | Stateless JWT (HS512), BCrypt passwords, role-based access, rate limits |
| Email | Spring Mail (Gmail SMTP) with FreeMarker templates |
| API docs | springdoc OpenAPI / Swagger UI (off by default) |

---

## Quick start with Docker

With [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed, the database, the
API and the web app run with one command (the web app's folder must sit next to this one):

```powershell
copy .env.example .env     # then fill in your passwords
docker compose up -d --build
```

Open **http://localhost:8081**. Installing Docker, running on **Kubernetes** (scaling, rolling
updates, health checks) and every command you need: **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

---

## Getting started (without Docker)

### 1. Requirements
- Java 17 or newer (JDK), Maven (or the included `mvnw`), MySQL 8.
- A Gmail account with an **App Password** for sending emails (Google Account → Security →
  2-Step Verification → App passwords).

### 2. Configure
Secrets are **never** written in the code or in `application.properties`; they come from
environment variables:

| Variable | What it is | Required |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL user and password | yes |
| `JWT_SECRET` | Long random text (32+ characters) that signs log-in tokens | yes (otherwise everyone is logged out on every restart) |
| `MAIL_PASSWORD` | Gmail App Password (16 letters) | yes |
| `CORS_ORIGINS` | Web addresses allowed to call the API, comma-separated | no (default: `http://localhost:5173,http://localhost:4173`) |
| `DOCS_PUBLIC` | `true` to turn on Swagger UI at `/swagger-ui.html` | no |
| `PAYMENTS_REQUIRED` | `false` to book without paying | no (default `true`) |
| `RATE_LIMIT` | `false` to switch off per-client rate limits | no (default `true`) |
| `TRUST_PROXY` | `true` behind nginx, Docker or Kubernetes: the client's address comes from `X-Forwarded-For` | no (default `false`) |

Also edit these two lines in `src/main/resources/application.properties` (they aren't secret):
- `spring.datasource.url` — use `localhost` if MySQL runs on your machine. The database
  `railway_boot_api` is created automatically.
- `spring.mail.username` — your Gmail address.

Make a JWT secret in PowerShell:
```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

**In Spring Tool Suite / Eclipse:** Run → Run Configurations → your app → **Environment** tab → add
each variable → Apply → Run.

**From a terminal:**
```powershell
$env:DB_USERNAME="root"; $env:DB_PASSWORD="..."; $env:JWT_SECRET="..."; $env:MAIL_PASSWORD="..."
.\mvnw.cmd spring-boot:run        # macOS / Linux: ./mvnw spring-boot:run
```

The API starts on **http://localhost:8080**. On first start it fills the station directory with a
starter list of stations.

### 3. Create the admin
There is one admin account. Create it in the web app at `/console/create`, or:
```http
POST /admin/create
{"name": "admin", "password": "a-strong-password", "email": "you@example.com"}
```

### 4. Add trains
Log in to the admin console and either **Add a train** step by step, or **Import trains → Import
Karnataka trains** (438 trains; every Karnataka station joins the station directory). It takes a few
minutes; try "At most 20" first if you only want a quick look.

### 5. Run the tests
```powershell
.\mvnw.cmd test -Dtest="*RulesTest,TrainImportTest"
```
(`.\mvnw.cmd test` also runs `RailwayApplicationTests`, which starts the whole app and needs MySQL
and the environment variables above.) Unit tests cover seat allocation, quotas, RAC, refunds, day numbers, travel rules, companion
matching, the train importer (including the bundled data), payments and rate limits.

---

## Test payments

Every booking needs a payment through the built-in test gateway:

| Method | Succeeds with | Fails with |
|---|---|---|
| Card | `4111 1111 1111 1111`, any future expiry, any CVV, OTP `123456` | `4000 0000 0000 0002` (declined), wrong OTP 3 times |
| UPI | any id like `name@bank`, then Approve | `failure@upi`, or Decline |
| Net banking | choose a bank, then Approve | Decline |

Card numbers and CVVs are never stored: only the brand and last 4 digits. Payments that are paid but
not used for a booking are refunded after 30 minutes.

---

## How booking works (short version)

1. **Search**: the date is the day the passenger boards. For a stop on Day 2 of a train's run, seats
   belong to the run that started the day before.
2. **Plan**: the server checks the train, class, quota, Tatkal opening time and chart status, and
   works out the distance and fare.
3. **Book** (one database transaction): lock the payment, then free seats for that stretch
   (`SELECT … FOR UPDATE SKIP LOCKED`), so people booking at the same moment never get the same
   berth. Leftover passengers get RAC, then a waitlist number. Seats can be reused on different
   stretches of the route.
4. **Cancel**: the refund follows the cancellation rules; the freed berth goes to RAC, then the
   waitlist, and RAC room goes to the waitlist.
5. **Chart** (automatic, 4 hours before the train starts): unsold quota seats go to RAC and the
   waitlist, RAC passengers are paired on RAC berths, Vikalp bookings try another train, and anyone
   still waitlisted is dropped with a refund. Everyone is emailed their final status.

Details: [docs/SEAT-ALLOCATION.md](docs/SEAT-ALLOCATION.md). Security notes:
[docs/SECURITY.md](docs/SECURITY.md). What changed in each version: [docs/CHANGELOG.md](docs/CHANGELOG.md).

---

## Main API endpoints

All responses look like `{"status": 200, "message": "...", "data": ..., "data2": ...}`.
Logged-in requests send the token in a header named `Bearer`.

**Public (no log-in)**

| | |
|---|---|
| `GET /customer/stations?q=` | Station search |
| `GET /customer/pnr-status/{pnr}` | PNR status without names |
| `GET /customer/chart/{train}?date=` | Chart & vacancy (after the chart) |
| `GET /customer/running/{train}?date=` | Live running status |
| `GET /customer/timetable/{train}`, `GET /customer/train-search?q=` | Timetables |
| `GET /customer/verify-ticket?code=` | Check an e-ticket QR code |

**Passengers**

| | |
|---|---|
| `POST /customer/signup`, `/login`, `/verify_otp`, `/logout` | Account |
| `GET /customer/trains?from=&to=&date=` | Trains between two places |
| `GET /customer/connections?from=&to=&date=` | Journeys with one change |
| `GET /customer/select_class/{train}/{from}/{to}?seat_type=&date=&quota=` | Availability and fare |
| `POST /customer/payments`, `/payments/{id}/confirm` | Test payment |
| `POST /customer/booking/{train}?seat_type=&book_date=&from=&to=&quota=&payment_id=&vikalp=` | Book |
| `GET /customer/my_bookings`, `GET /customer/pnr/{pnr}` | Bookings |
| `POST /customer/cancel_preview/{pnr}`, `POST /customer/cancel/{pnr}` | Refund preview, cancel |
| `GET/POST/DELETE /customer/saved_passengers` | Saved passengers |
| `GET /customer/ticket-code/{pnr}` | Signed code for the e-ticket QR |

**Admin** (token of the admin)

| | |
|---|---|
| `POST /admin/login` | Log in |
| `GET /admin/trains?q=&limit=`, `GET /admin/trains/{no}` | Trains |
| `POST /admin/addtrain`, `/schedule`, `/addstations/{no}`, `/addRoute/{no}`, `/addCoaches/{no}` | Set up a train |
| `POST /admin/coaches/{no}/add`, `/remove` | Coaches from a date |
| `GET/POST /admin/quotas/{no}` | Quotas and RAC berths |
| `GET /admin/chart/{no}?date=` | Chart with passengers |
| `POST /admin/runs/{no}/cancel`, `/reschedule`, `/position` | Train runs |
| `GET /admin/reports?from=&to=` | Reports |
| `GET/POST /admin/import/bundled`, `POST /admin/import/upload`, `GET /admin/import/status` | Bulk import |
| `GET /admin/customers`, `POST /admin/customers/{id}/block`, `/unblock` | Customers |
| `GET /admin/audit` | Audit log |

With `DOCS_PUBLIC=true`, Swagger UI lists every endpoint with its parameters.

---

## Project structure

```
src/main/java/com/project/railway
├── configuration   security (JWT filter, rate limits, CORS), chart scheduler, startup data
├── controller      REST endpoints: Customer_, Booking_ and Admin_Controller
├── dto             JPA entities (Train, Station, Coach, CoachSeat, Booking, Passenger, Payment…) and views
├── helper          plain-Java rules, unit-tested: SeatPicker, QuotaPlanner, RacRules, RefundRules,
│                   StopDays, TatkalRules, TravelRules, CompanionRules, TrainImport, PaymentRules…
├── repository      Spring Data repositories (seat locking queries live in CoachSeat_Repository)
└── service         BookingEngine (transactions), BookingService (checks), ChartService, RunService,
                    PaymentService, VikalpService, ImportService, InfoService, AuditService…
src/main/resources
├── application.properties
├── templates       email templates (ticket, OTP, admin log-in)
└── data            bundled Karnataka train list (gzipped JSON)
tools/build-karnataka-railways.py   rebuilds the bundled list (DataMeet data + Vande Bharat timetables)
tools/build-indian-railways.py      the same for all of India, if you ever want it back
Dockerfile, docker-compose.yml      containers: API, MySQL and the web app
k8s/                                Kubernetes manifests (kubectl apply -k k8s/)
docs/                               deployment, seat allocation, security, changelog
```

---

## Data credits

The bundled Karnataka list is built from [DataMeet's Indian Railways data](https://github.com/datameet/railways)
(CC0, collected in 2016): every station inside Karnataka, and every train with at least two stops in
Karnataka, cut to its stops in the state. Those timings may have changed since, and those trains run
daily because the data has no running days. Passenger, MEMU and DEMU trains get second-sitting (2S)
coaches.

The 12 **Vande Bharat** trains (20661/62 Bengaluru–Dharwad, 26751/52 Belagavi–Bengaluru, 20607/08
Chennai–Mysuru, 22231/32 Kalaburagi–SMVT Bengaluru, 20645/46 Mangaluru–Madgaon, 20669/70
Hubballi–Pune) were added by hand from public timetables (2025), with their running days, chair car
(CC) and executive (EC) coaches and full routes. Check timings against official sources before
relying on them.
