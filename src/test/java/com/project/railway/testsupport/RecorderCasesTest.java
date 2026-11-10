package com.project.railway.testsupport;

import static com.project.railway.testsupport.CaseTable.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Checks the recorder itself: a wrong result, an exception and a missing exception are each
 * recorded as FAIL with the reason. (These tables never call done(), so they don't appear in the
 * report and don't fail the build.)
 */
class RecorderCasesTest {

	@Test
	void failuresAreRecordedWithTheirReason() {
		CaseTable t = CaseTable.of("Recorder", "self-check");
		t.check("right", in("x", 1), 2, () -> 1 + 1);
		t.check("wrong", in("x", 1), 3, () -> 1 + 1);
		t.check("throws", in("x", "abc"), 1, () -> Integer.parseInt("abc"));
		t.checkThrows("doesn't throw", in("x", "5"), NumberFormatException.class, null, () -> Integer.parseInt("5"));
		t.check("numbers within 0.005 are equal", in("x", 0.1), 0.3, () -> 0.1 + 0.2);
		t.check("arrays compare by content", in("x", "[1, 2]"), new int[] { 1, 2 }, () -> new int[] { 1, 2 });
		List<CaseTable.Row> rows = t.rows();
		assertEquals(6, rows.size());
		assertTrue(rows.get(0).passed());
		assertFalse(rows.get(1).passed());
		assertTrue(rows.get(1).reason().startsWith("Expected 3 but got 2"), rows.get(1).reason());
		assertTrue(rows.get(1).reason().contains("RecorderCasesTest.java:"), rows.get(1).reason());
		assertFalse(rows.get(2).passed());
		assertTrue(rows.get(2).reason().contains("threw NumberFormatException"), rows.get(2).reason());
		assertFalse(rows.get(3).passed());
		assertTrue(rows.get(3).reason().contains("finished normally"), rows.get(3).reason());
		assertTrue(rows.get(4).passed());
		assertTrue(rows.get(5).passed());
		assertEquals("x = 1", rows.get(0).inputs());
	}
}
