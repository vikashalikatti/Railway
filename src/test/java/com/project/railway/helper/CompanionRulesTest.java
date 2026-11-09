package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CompanionRulesTest {

	@Test
	void soloWomanLooksForWomenOnlyWhenAlone() {
		assertEquals(SeatPicker.Near.WOMEN, CompanionRules.seeks(26, "Female", false, false, 1));
		assertEquals(0, CompanionRules.seeks(26, "Female", false, false, 2)); // with family
		assertEquals(0, CompanionRules.seeks(26, "Male", false, false, 1));
		assertEquals(0, CompanionRules.seeks(9, "Female", false, false, 1)); // child
		assertEquals(SeatPicker.Near.WOMEN | SeatPicker.Near.HELPER, CompanionRules.seeks(70, "female", true, false, 1));
		assertEquals(SeatPicker.Near.SENIOR_NEEDING_HELP, CompanionRules.seeks(30, "Male", false, true, 3));
	}

	@Test
	void whoCanAskAndWhoCanHelp() {
		assertTrue(CompanionRules.canAskForAssistance(65, 1));
		assertFalse(CompanionRules.canAskForAssistance(65, 2));
		assertFalse(CompanionRules.canAskForAssistance(59, 1));
		assertTrue(CompanionRules.canHelp(18));
		assertFalse(CompanionRules.canHelp(17));
		assertFalse(CompanionRules.canHelp(60));
	}

	@Test
	void baysAreFlaggedWithoutNames() {
		Map<String, Integer> near = CompanionRules.nearFlags(List.of(
				new CompanionRules.Neighbour("S1", 2, "Female", 34, false, false, false),
				new CompanionRules.Neighbour("S1", 3, "Female", 15, false, false, false), // girl: not counted
				new CompanionRules.Neighbour("S2", 1, "Male", 40, false, true, false),
				new CompanionRules.Neighbour("S2", 4, "Male", 40, false, true, true), // helper already matched
				new CompanionRules.Neighbour("S3", 5, "Male", 72, true, false, false)));
		assertEquals(SeatPicker.Near.WOMEN, near.get("S1#2"));
		assertEquals(null, near.get("S1#3"));
		assertEquals(SeatPicker.Near.HELPER, near.get("S2#1"));
		assertEquals(null, near.get("S2#4"));
		assertEquals(SeatPicker.Near.SENIOR_NEEDING_HELP, near.get("S3#5"));
	}

	@Test
	void soloWomanGetsTheBerthInTheWomensBay() {
		List<SeatPicker.Seat> seats = List.of(new SeatPicker.Seat(1, "S1", 1, "LOWER", 1, 0),
				new SeatPicker.Seat(2, "S1", 9, "UPPER", 2, SeatPicker.Near.WOMEN));
		var pick = SeatPicker.assign(List.of(new SeatPicker.Traveller(0, 28, null, SeatPicker.Near.WOMEN)), seats);
		assertEquals(2L, pick.get(0).id());
		// without looking for company the lower berth (first seat) wins
		var plain = SeatPicker.assign(List.of(new SeatPicker.Traveller(0, 28, null)), seats);
		assertEquals(1L, plain.get(0).id());
	}

	@Test
	void seniorIsMatchedWithAHelperOnBoardInTheSameCoach() {
		var senior = new CompanionRules.Candidate(10, 100, "S1", 3, 2, 5);
		var otherCoach = new CompanionRules.Candidate(11, 101, "S2", 3, 0, 5);
		var boardsLater = new CompanionRules.Candidate(12, 102, "S1", 3, 3, 5);
		var leavesEarly = new CompanionRules.Candidate(13, 103, "S1", 3, 0, 4);
		var staysFar = new CompanionRules.Candidate(14, 104, "S1", 8, 1, 6);
		var staysNear = new CompanionRules.Candidate(15, 105, "S1", 4, 1, 6);
		var m = CompanionRules.match(List.of(senior), List.of(otherCoach, boardsLater, leavesEarly, staysFar, staysNear));
		assertEquals(1, m.size());
		assertEquals(15L, m.get(0).helper().id());
	}

	@Test
	void oneHelperPerSeniorAndNeverOnTheirOwnTicket() {
		var s1 = new CompanionRules.Candidate(1, 100, "B1", 1, 0, 3);
		var s2 = new CompanionRules.Candidate(2, 101, "B1", 2, 0, 3);
		var sameTicket = new CompanionRules.Candidate(3, 100, "B1", 1, 0, 3);
		var h = new CompanionRules.Candidate(4, 102, "B1", 1, 0, 3);
		var m = CompanionRules.match(List.of(s2, s1), List.of(sameTicket, h));
		assertEquals(2, m.size());
		assertEquals(1L, m.get(0).senior().id()); // lowest id first
		assertEquals(4L, m.get(0).helper().id()); // not the helper on senior 1's own ticket
		assertEquals(3L, m.get(1).helper().id());
		assertEquals(1, CompanionRules.match(List.of(s1), List.of(sameTicket, h)).size());
		assertEquals("Ravi", CompanionRules.firstName("Ravi Kumar"));
	}
}
