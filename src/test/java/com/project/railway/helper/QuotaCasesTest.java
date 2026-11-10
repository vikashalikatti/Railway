package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.Quota.Traveller;
import com.project.railway.helper.QuotaPlanner.SeatRef;
import com.project.railway.helper.QuotaPlanner.Shares;
import com.project.railway.testsupport.CaseTable;

/** Quotas: codes, which classes offer them, who may book them, and how seats are split among them */
class QuotaCasesTest {

	@Test
	void quotaCodes() {
		CaseTable t = CaseTable.of("Quotas", "Quota codes and what each quota allows");
		t.check("Missing code means General", in("code", null), Quota.GN, () -> Quota.parse(null));
		t.check("Blank code means General", in("code", " "), Quota.GN, () -> Quota.parse(" "));
		t.check("Lower case", in("code", "tq"), Quota.TQ, () -> Quota.parse("tq"));
		t.check("Spaces around", in("code", " pt "), Quota.PT, () -> Quota.parse(" pt "));
		t.checkThrows("Unknown code", in("code", "XX"), IllegalArgumentException.class, null, () -> Quota.parse("XX"));
		Object[][] q = {
				// quota, label, waitlist prefix, has waitlist, max passengers, tatkal, bookable, gets RAC
				{ Quota.GN, "General", "WL", true, 6, false, true, true }, { Quota.TQ, "Tatkal", "TQWL", true, 4, true, true, true },
				{ Quota.PT, "Premium Tatkal", "", false, 4, true, true, false }, { Quota.LD, "Ladies", "", false, 6, false, true, false },
				{ Quota.SS, "Senior citizen", "", false, 2, false, true, false }, { Quota.PH, "Divyangjan", "", false, 2, false, true, false },
				{ Quota.RC, "RAC berth", "", false, 0, false, false, false } };
		for (Object[] c : q) {
			Quota x = (Quota) c[0];
			t.check(x + " label", in("quota", x), c[1], () -> x.label);
			t.check(x + " waitlist prefix", in("quota", x), c[2], () -> x.wlPrefix);
			t.check(x + " has a waitlist?", in("quota", x), c[3], () -> x.hasWaitlist);
			t.check(x + " most passengers per booking", in("quota", x), c[4], () -> x.maxPassengers);
			t.check(x + " is Tatkal?", in("quota", x), c[5], () -> x.isTatkal());
			t.check(x + " can be booked?", in("quota", x), c[6], () -> x.bookable());
			t.check(x + " gets RAC when full?", in("quota", x), c[7], () -> x.hasRac());
		}
		t.done();
	}

	@Test
	void quotasPerClass() {
		CaseTable t = CaseTable.of("Quotas", "Which classes sell each quota, and waitlist counter keys");
		Object[][] cases = { { Quota.TQ, AC1_TIER, false }, { Quota.PT, AC1_TIER, false }, { Quota.TQ, SLEEPER_CLASS, true },
				{ Quota.TQ, AC_CHAIR_CAR, true }, { Quota.SS, AC_CHAIR_CAR, false }, { Quota.SS, SECOND_CLASS, false },
				{ Quota.SS, SLEEPER_CLASS, true }, { Quota.SS, AC1_TIER, true }, { Quota.RC, AC_CHAIR_CAR, false },
				{ Quota.RC, SLEEPER_CLASS, true }, { Quota.RC, AC1_TIER, false }, { Quota.PH, AC_CHAIR_CAR, true },
				{ Quota.LD, EXECUTIVE_CHAIR_CAR, true }, { Quota.GN, SECOND_CLASS, true } };
		for (Object[] c : cases) {
			Quota q = (Quota) c[0];
			Seat_type type = (Seat_type) c[1];
			t.check(q + " in " + CoachLayout.shortCode(type), in("quota", q, "class", CoachLayout.shortCode(type)), c[2], () -> q.offeredIn(type));
		}
		t.check("General keeps the plain class name", in("quota", "GN", "class", "SL"), "SLEEPER_CLASS", () -> Quota.GN.counterKey(SLEEPER_CLASS));
		t.check("Tatkal counter", in("quota", "TQ", "class", "SL"), "TQ:SL", () -> Quota.TQ.counterKey(SLEEPER_CLASS));
		t.check("Premium Tatkal counter", in("quota", "PT", "class", "3A"), "PT:3A", () -> Quota.PT.counterKey(AC3_TIER));
		t.done();
	}

	private static List<Traveller> people(Object... ageGender) {
		List<Traveller> out = new ArrayList<>();
		for (int i = 0; i < ageGender.length; i += 2) {
			out.add(new Traveller((Integer) ageGender[i], (String) ageGender[i + 1]));
		}
		return out;
	}

	@Test
	void whoMayBook() {
		CaseTable t = CaseTable.of("Quotas", "Who may book in each quota (null = allowed)");
		Object[][] cases = {
				{ "General, 6 passengers", Quota.GN, people(30, "Male", 28, "Female", 5, "Male", 60, "Male", 58, "Female", 20, "Male"), null },
				{ "General, 7 passengers", Quota.GN, people(30, "Male", 28, "Female", 5, "Male", 60, "Male", 58, "Female", 20, "Male", 21, "Male"),
						"General allows up to 6 passengers per booking." },
				{ "Tatkal, 5 passengers", Quota.TQ, people(30, "Male", 28, "Female", 5, "Male", 60, "Male", 58, "Female"),
						"Tatkal allows up to 4 passengers per booking." },
				{ "Ladies: two women", Quota.LD, people(30, "Female", 25, "female"), null },
				{ "Ladies: woman with her son aged 10", Quota.LD, people(35, "Female", 10, "Male"), null },
				{ "Ladies: woman with a man", Quota.LD, people(35, "Female", 36, "Male"),
						"Ladies quota is for women, and children under 12 travelling with them." },
				{ "Ladies: woman with her son aged 12", Quota.LD, people(35, "Female", 12, "Male"),
						"Ladies quota is for women, and children under 12 travelling with them." },
				{ "Ladies: girl of 10 alone", Quota.LD, people(10, "Female"), "Ladies quota needs at least one woman aged 12 or over." },
				{ "Senior: man aged 60", Quota.SS, people(60, "Male"), null },
				{ "Senior: man aged 59", Quota.SS, people(59, "Male"), "Senior citizen quota is for men aged 60+ and women aged 45+." },
				{ "Senior: woman aged 45", Quota.SS, people(45, "Female"), null },
				{ "Senior: woman aged 44", Quota.SS, people(44, "Female"), "Senior citizen quota is for men aged 60+ and women aged 45+." },
				{ "Senior: couple 62 and 50", Quota.SS, people(62, "Male", 50, "Female"), null },
				{ "Senior: 3 passengers", Quota.SS, people(62, "Male", 50, "Female", 70, "Male"), "Senior citizen allows up to 2 passengers per booking." },
				{ "RAC berths can't be booked", Quota.RC, people(30, "Male"),
						"RAC berths can't be booked directly. Book in General quota: RAC is given when the class is full." } };
		for (Object[] c : cases) {
			Quota q = (Quota) c[1];
			@SuppressWarnings("unchecked")
			List<Traveller> p = (List<Traveller>) c[2];
			t.check((String) c[0], in("quota", q, "passengers", p), c[3], () -> q.eligibility(p));
		}
		t.check("Divyangjan without a card holder", in("quota", "PH", "passengers", "30 M (no card)"),
				"Divyangjan quota needs a passenger with a disability concession card.",
				() -> Quota.PH.eligibility(List.of(new Traveller(30, "Male", false))));
		t.check("Divyangjan: card holder and escort", in("quota", "PH", "passengers", "30 M (card), 32 F (escort)"), null,
				() -> Quota.PH.eligibility(List.of(new Traveller(30, "Male", true), new Traveller(32, "Female", false))));
		t.check("Divyangjan: 3 passengers", in("quota", "PH", "passengers", "3, one with card"), "Divyangjan allows up to 2 passengers per booking.",
				() -> Quota.PH.eligibility(List.of(new Traveller(30, "Male", true), new Traveller(32, "Female"), new Traveller(5, "Male"))));
		t.done();
	}

	@Test
	void quotaShares() {
		CaseTable t = CaseTable.of("Quotas", "Default quota shares per class and the admin's limits");
		t.check("SL defaults", in("class", "SL"), new Shares(30, 10, 5, 8, 4, 2), () -> Shares.defaults(SLEEPER_CLASS));
		t.check("2A defaults", in("class", "2A"), new Shares(30, 10, 5, 8, 1, 2), () -> Shares.defaults(AC2_TIER));
		t.check("1A defaults: no Tatkal", in("class", "1A"), new Shares(0, 0, 5, 10, 0, 0), () -> Shares.defaults(AC1_TIER));
		t.check("CC defaults: no senior quota", in("class", "CC"), new Shares(30, 10, 5, 0, 0, 0), () -> Shares.defaults(AC_CHAIR_CAR));
		t.check("Total of SL defaults", in("class", "SL"), 55, () -> Shares.defaults(SLEEPER_CLASS).total());
		Object[][] problems = {
				{ "Valid", new Shares(30, 10, 5, 8, 4, 2), SLEEPER_CLASS, null },
				{ "Negative", new Shares(-1, 10, 5, 8), SLEEPER_CLASS, "Quota shares can't be negative" },
				{ "91% in quotas", new Shares(50, 30, 6, 5), SLEEPER_CLASS, "Quotas can take at most 90% of a class; keep some seats for General" },
				{ "Exactly 90% is fine", new Shares(50, 30, 5, 5), SLEEPER_CLASS, null },
				{ "Tatkal in 1A", new Shares(10, 0, 5, 10), AC1_TIER, "There is no Tatkal in 1A" },
				{ "Senior quota in CC", new Shares(30, 10, 5, 8), AC_CHAIR_CAR, "Senior citizen quota needs lower berths, which CC doesn't have" },
				{ "RAC berths in CC", new Shares(30, 10, 5, 0, 2, 0), AC_CHAIR_CAR, "CC has no side-lower berths for RAC" },
				{ "10 RAC berths in an SL coach (it has 9 side-lowers)", new Shares(30, 10, 5, 8, 10, 2), SLEEPER_CLASS, "At most 9 RAC berths per SL coach" } };
		for (Object[] c : problems) {
			Shares s = (Shares) c[1];
			Seat_type type = (Seat_type) c[2];
			t.check((String) c[0], in("shares", s, "class", CoachLayout.shortCode(type)), c[3], () -> s.problem(type));
		}
		t.done();
	}

	/** One Sleeper coach (72 berths) or several, as the planner sees them */
	private static List<SeatRef> coaches(Seat_type type, int count) {
		List<SeatRef> out = new ArrayList<>();
		long id = 1;
		for (int c = 1; c <= count; c++) {
			for (int s = 1; s <= CoachLayout.capacity(type); s++) {
				out.add(new SeatRef(id++, c, s, CoachLayout.berthType(type, s)));
			}
		}
		return out;
	}

	private static Map<Quota, Integer> count(Map<Long, Quota> plan) {
		Map<Quota, Integer> out = new EnumMap<>(Quota.class);
		plan.values().forEach(q -> out.merge(q, 1, Integer::sum));
		return out;
	}

	private static List<Integer> seatsIn(List<SeatRef> seats, Map<Long, Quota> plan, Quota q) {
		List<Integer> out = new ArrayList<>();
		seats.forEach(s -> {
			if (plan.get(s.id()) == q) {
				out.add(s.seatNumber());
			}
		});
		return out;
	}

	@Test
	void splittingSeats() {
		CaseTable t = CaseTable.of("Quotas", "Splitting a class's seats into quotas");
		List<SeatRef> sl = coaches(SLEEPER_CLASS, 1);
		Shares def = Shares.defaults(SLEEPER_CLASS);
		Map<Long, Quota> plan = QuotaPlanner.plan(sl, def);
		var inputs = in("class", "SL", "coaches", 1, "seats", 72, "shares", def);
		t.check("Seats per quota", inputs, Map.of(Quota.GN, 30, Quota.RC, 4, Quota.PH, 2, Quota.SS, 5, Quota.LD, 3, Quota.PT, 7, Quota.TQ, 21),
				() -> Map.copyOf(count(plan)));
		t.check("RAC berths: the last 4 side-lowers", inputs, List.of(47, 55, 63, 71), () -> seatsIn(sl, plan, Quota.RC));
		t.check("Divyangjan: the first lower berths", inputs, List.of(1, 4), () -> seatsIn(sl, plan, Quota.PH));
		t.check("Senior citizen: only lower berths", inputs, true,
				() -> seatsIn(sl, plan, Quota.SS).stream().allMatch(n -> CoachLayout.isLower(CoachLayout.berthType(SLEEPER_CLASS, n))));
		t.check("Ladies: a block at the start of the coach", inputs, List.of(2, 3, 5), () -> seatsIn(sl, plan, Quota.LD));
		t.check("Premium Tatkal: from the end of the coach", inputs, List.of(65, 66, 67, 68, 69, 70, 72), () -> seatsIn(sl, plan, Quota.PT));

		List<SeatRef> two = coaches(SLEEPER_CLASS, 2);
		Map<Long, Quota> plan2 = QuotaPlanner.plan(two, new Shares(0, 0, 0, 10));
		t.check("Senior berths spread over both coaches", in("class", "SL", "coaches", 2, "senior %", 10), List.of(7, 7), () -> {
			int c1 = 0;
			int c2 = 0;
			for (SeatRef s : two) {
				if (plan2.get(s.id()) == Quota.SS) {
					if (s.coachId() == 1) {
						c1++;
					} else {
						c2++;
					}
				}
			}
			return List.of(c1, c2);
		});
		t.check("No shares: every seat General", in("class", "CC", "shares", "all 0"), Map.of(Quota.GN, 78),
				() -> Map.copyOf(count(QuotaPlanner.plan(coaches(AC_CHAIR_CAR, 1), new Shares(0, 0, 0, 0)))));
		t.check("No seats: empty plan", in("seats", 0), Map.of(), () -> QuotaPlanner.plan(List.of(), def));
		t.check("Divyangjan in a chair car (no lower berths): any seats", in("class", "CC", "divyang %", 2), 2,
				() -> count(QuotaPlanner.plan(coaches(AC_CHAIR_CAR, 1), new Shares(0, 0, 0, 0, 0, 2))).get(Quota.PH));
		t.done();
	}
}
