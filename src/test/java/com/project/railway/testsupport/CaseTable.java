package com.project.railway.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A table of test cases with their records: every case keeps its input values, the expected and
 * the actual result, PASS or FAIL, and why it failed. One failing case doesn't stop the others:
 * every case runs, and the test fails at the end listing the failed ones.
 *
 * After each table the report is written again (so it is complete even if a later test crashes):
 * <pre>
 *   target/test-records/test-records.html   open in a browser: summary, filter "only failed"
 *   target/test-records/test-records.csv    open in Excel
 * </pre>
 * Change the folder with -Dtest.records.dir=...
 *
 * <pre>
 * CaseTable t = CaseTable.of("Fares", "Fare per passenger");
 * t.check("Sleeper, 100 km", in("class", "SL", "km", 100), 90.0, () -> FareCalculator.perPassenger(SL, 100, 0));
 * t.done();
 * </pre>
 */
public final class CaseTable {

	/** One recorded case */
	public record Row(String suite, String rule, int number, String name, String inputs, String expected, String actual,
			boolean passed, String reason, String where, long micros) {
	}

	private static final List<Row> ALL = new ArrayList<>();
	private static final String STARTED = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH));
	private static final double TOLERANCE = 0.005;

	private final String suite;
	private final String rule;
	private final List<Row> rows = new ArrayList<>();

	private CaseTable(String suite, String rule) {
		this.suite = suite;
		this.rule = rule;
	}

	/** suite: the area being tested (e.g. "Refunds"); rule: what this table checks */
	public static CaseTable of(String suite, String rule) {
		return new CaseTable(suite, rule);
	}

	/** Input values in order: in("class", "SL", "km", 100) */
	public static Map<String, Object> in(Object... keysAndValues) {
		if (keysAndValues.length % 2 != 0) {
			throw new IllegalArgumentException("in(...) takes name, value pairs");
		}
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			m.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
		}
		return m;
	}

	/* ------------------------------------------------------------------ checks */

	/** The case passes when the actual value equals the expected one (numbers within 0.005) */
	public CaseTable check(String name, Map<String, Object> inputs, Object expected, Supplier<?> actual) {
		long t0 = System.nanoTime();
		String where = caller();
		Object got;
		try {
			got = actual.get();
		} catch (Throwable e) {
			return add(name, inputs, show(expected), "threw " + describe(e), false,
					"Expected " + show(expected) + " but it threw " + describe(e) + thrownAt(e), where, t0);
		}
		boolean ok = same(expected, got);
		return add(name, inputs, show(expected), show(got), ok,
				ok ? "" : "Expected " + show(expected) + " but got " + show(got), where, t0);
	}

	/** The case passes when ok(actual) is true; expectation describes what was wanted */
	public <T> CaseTable checkThat(String name, Map<String, Object> inputs, String expectation, Supplier<T> actual,
			Predicate<T> ok) {
		long t0 = System.nanoTime();
		String where = caller();
		T got;
		try {
			got = actual.get();
		} catch (Throwable e) {
			return add(name, inputs, expectation, "threw " + describe(e), false,
					"Expected " + expectation + " but it threw " + describe(e) + thrownAt(e), where, t0);
		}
		boolean passed;
		String reason = "";
		try {
			passed = ok.test(got);
			if (!passed) {
				reason = "Expected " + expectation + " but got " + show(got);
			}
		} catch (Throwable e) {
			passed = false;
			reason = "The check itself threw " + describe(e);
		}
		return add(name, inputs, expectation, show(got), passed, reason, where, t0);
	}

	/** The case passes when the code throws this kind of exception, with messagePart in its message (null: any message) */
	public CaseTable checkThrows(String name, Map<String, Object> inputs, Class<? extends Throwable> type, String messagePart,
			Runnable code) {
		long t0 = System.nanoTime();
		String where = caller();
		String expected = type.getSimpleName() + (messagePart == null ? "" : " mentioning \"" + messagePart + "\"");
		try {
			code.run();
		} catch (Throwable e) {
			boolean ok = type.isInstance(e) && (messagePart == null || String.valueOf(e.getMessage()).contains(messagePart));
			return add(name, inputs, "throws " + expected, "threw " + describe(e), ok,
					ok ? "" : "Expected it to throw " + expected + " but it threw " + describe(e), where, t0);
		}
		return add(name, inputs, "throws " + expected, "no exception", false,
				"Expected it to throw " + expected + " but it finished normally", where, t0);
	}

	/**
	 * Ends the table: writes the report, prints a summary line, and fails the test if any case
	 * failed (listing every failed case with its reason).
	 */
	public void done() {
		synchronized (ALL) {
			ALL.addAll(rows);
			writeReport(new ArrayList<>(ALL));
		}
		long failed = rows.stream().filter(r -> !r.passed()).count();
		System.out.println("[test-records] " + suite + " / " + rule + ": " + rows.size() + " cases, "
				+ (rows.size() - failed) + " passed, " + failed + " failed");
		if (failed > 0) {
			StringBuilder sb = new StringBuilder(failed + " of " + rows.size() + " cases failed in \"" + rule + "\":");
			for (Row r : rows) {
				if (!r.passed()) {
					sb.append("\n  #").append(r.number()).append(' ').append(r.name()).append(" [").append(r.inputs())
							.append("]: ").append(r.reason());
				}
			}
			sb.append("\nSee target/test-records/test-records.html");
			throw new AssertionError(sb.toString());
		}
	}

	/** This table's cases so far (before done()) */
	public List<Row> rows() {
		return List.copyOf(rows);
	}

	/** Recorded rows so far in this JVM (for tests of the recorder itself) */
	public static List<Row> recorded() {
		synchronized (ALL) {
			return List.copyOf(ALL);
		}
	}

	/* ------------------------------------------------------------------ internals */

	private CaseTable add(String name, Map<String, Object> inputs, String expected, String actual, boolean passed,
			String reason, String where, long t0) {
		rows.add(new Row(suite, rule, rows.size() + 1, name, showInputs(inputs), expected, actual, passed,
				passed ? "" : reason + " (" + where + ")", where, (System.nanoTime() - t0) / 1000));
		return this;
	}

	static boolean same(Object expected, Object actual) {
		if (expected instanceof Number e && actual instanceof Number a && !(expected instanceof Long && actual instanceof Long)) {
			return Math.abs(e.doubleValue() - a.doubleValue()) <= TOLERANCE;
		}
		if (expected != null && actual != null && expected.getClass().isArray() && actual.getClass().isArray()) {
			return java.util.Objects.deepEquals(expected, actual);
		}
		return Objects.equals(expected, actual);
	}

	static String show(Object v) {
		if (v == null) {
			return "null";
		}
		if (v instanceof Double || v instanceof Float) {
			double d = ((Number) v).doubleValue();
			return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
		}
		if (v instanceof String s) {
			return "\"" + s + "\"";
		}
		if (v instanceof int[] a) {
			return java.util.Arrays.toString(a);
		}
		if (v instanceof Collection<?> c) {
			List<String> out = new ArrayList<>();
			c.forEach(x -> out.add(show(x)));
			return "[" + String.join(", ", out) + "]";
		}
		if (v instanceof Map<?, ?> m) {
			List<String> out = new ArrayList<>();
			m.forEach((k, x) -> out.add(k + "=" + show(x)));
			return "{" + String.join(", ", out) + "}";
		}
		return String.valueOf(v);
	}

	private static String showInputs(Map<String, Object> inputs) {
		if (inputs == null || inputs.isEmpty()) {
			return "";
		}
		List<String> out = new ArrayList<>();
		inputs.forEach((k, v) -> out.add(k + " = " + show(v)));
		return String.join("; ", out);
	}

	private static String describe(Throwable e) {
		return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
	}

	/** Where in the application code the exception came from */
	private static String thrownAt(Throwable e) {
		for (StackTraceElement f : e.getStackTrace()) {
			if (f.getClassName().startsWith("com.project.railway") && !f.getClassName().contains("CasesTest")
					&& !f.getClassName().contains("testsupport")) {
				return " at " + f.getFileName() + ":" + f.getLineNumber();
			}
		}
		return "";
	}

	/** File and line of the check(...) call in the test */
	private static String caller() {
		for (StackTraceElement f : new Throwable().getStackTrace()) {
			if (!f.getClassName().equals(CaseTable.class.getName())) {
				return f.getFileName() + ":" + f.getLineNumber();
			}
		}
		return "";
	}

	/* ------------------------------------------------------------------ report */

	private static void writeReport(List<Row> rows) {
		Path dir = Path.of(System.getProperty("test.records.dir", "target/test-records"));
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("test-records.csv"), csv(rows), StandardCharsets.UTF_8);
			Files.writeString(dir.resolve("test-records.html"), html(rows), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Couldn't write the test records to " + dir.toAbsolutePath(), e);
		}
	}

	private static String csv(List<Row> rows) {
		StringBuilder sb = new StringBuilder("﻿"); // so Excel reads the file as UTF-8
		sb.append("Suite,Rule,Case no,Case,Input values,Expected,Actual,Result,Why it failed,Test line,Time (microseconds)\r\n");
		for (Row r : rows) {
			sb.append(String.join(",", q(r.suite()), q(r.rule()), String.valueOf(r.number()), q(r.name()), q(r.inputs()),
					q(r.expected()), q(r.actual()), r.passed() ? "PASS" : "FAIL", q(r.reason()), q(r.where()),
					String.valueOf(r.micros()))).append("\r\n");
		}
		return sb.toString();
	}

	private static String q(String s) {
		return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
	}

	private static String h(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String html(List<Row> rows) {
		long failed = rows.stream().filter(r -> !r.passed()).count();
		Map<String, List<Row>> bySuite = new LinkedHashMap<>();
		rows.forEach(r -> bySuite.computeIfAbsent(r.suite(), k -> new ArrayList<>()).add(r));

		StringBuilder sb = new StringBuilder();
		sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
				.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
				.append("<title>Railway test records</title><style>").append(CSS).append("</style></head><body>")
				.append("<header><h1>Railway test records</h1><p>Run started ").append(h(STARTED)).append("</p>")
				.append("<div class=\"totals\"><div><b>").append(rows.size()).append("</b>cases</div>")
				.append("<div class=\"ok\"><b>").append(rows.size() - failed).append("</b>passed</div>")
				.append("<div class=\"").append(failed > 0 ? "bad" : "").append("\"><b>").append(failed).append("</b>failed</div>")
				.append("<div><b>").append(bySuite.size()).append("</b>areas</div></div>")
				.append("<label class=\"only\"><input type=\"checkbox\" id=\"only\"> Show only failed cases</label>")
				.append("</header><main>");

		for (Map.Entry<String, List<Row>> e : bySuite.entrySet()) {
			List<Row> rs = e.getValue();
			long f = rs.stream().filter(r -> !r.passed()).count();
			sb.append("<details class=\"suite").append(f > 0 ? " has-fail\" open" : "\"").append("><summary><span class=\"name\">")
					.append(h(e.getKey())).append("</span><span class=\"count ").append(f > 0 ? "bad" : "ok").append("\">")
					.append(rs.size() - f).append(" / ").append(rs.size()).append(" passed</span></summary>");
			Map<String, List<Row>> byRule = new LinkedHashMap<>();
			rs.forEach(r -> byRule.computeIfAbsent(r.rule(), k -> new ArrayList<>()).add(r));
			for (Map.Entry<String, List<Row>> g : byRule.entrySet()) {
				boolean ruleFailed = g.getValue().stream().anyMatch(r -> !r.passed());
				sb.append("<section class=\"rule").append(ruleFailed ? " has-fail" : "").append("\"><h3>").append(h(g.getKey()))
						.append("</h3><div class=\"scroll\"><table><thead><tr>")
						.append("<th>#</th><th>Case</th><th>Input values</th><th>Expected</th><th>Actual</th>")
						.append("<th>Result</th><th>Why it failed</th></tr></thead><tbody>");
				for (Row r : g.getValue()) {
					sb.append("<tr class=\"").append(r.passed() ? "pass" : "fail").append("\"><td>").append(r.number())
							.append("</td><td>").append(h(r.name())).append("</td><td class=\"mono\">").append(h(r.inputs()))
							.append("</td><td class=\"mono\">").append(h(r.expected())).append("</td><td class=\"mono\">")
							.append(h(r.actual())).append("</td><td><span class=\"tag\">").append(r.passed() ? "PASS" : "FAIL")
							.append("</span></td><td>").append(h(r.reason())).append("</td></tr>");
				}
				sb.append("</tbody></table></div></section>");
			}
			sb.append("</details>");
		}
		sb.append("</main><script>").append(JS).append("</script></body></html>");
		return sb.toString();
	}

	private static final String CSS = """
			:root{--bg:#f5f6f8;--card:#fff;--ink:#1c2430;--muted:#5d6878;--line:#e3e7ee;--ok:#1f7a4a;--okbg:#e6f4ec;--bad:#b42318;--badbg:#fdecea}
			*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
			header{background:#1b3a73;color:#fff;padding:20px 24px}header h1{margin:0 0 2px;font-size:22px}header p{margin:0;opacity:.8}
			.totals{display:flex;gap:12px;flex-wrap:wrap;margin:16px 0 10px}.totals div{background:rgba(255,255,255,.12);border-radius:8px;padding:8px 14px;min-width:96px}
			.totals b{display:block;font-size:22px}.totals .ok b{color:#9ff0c2}.totals .bad{background:#b42318}
			.only{display:inline-flex;gap:6px;align-items:center;cursor:pointer}
			main{padding:16px;max-width:1400px;margin:0 auto}
			.suite{background:var(--card);border:1px solid var(--line);border-radius:10px;margin-bottom:12px;overflow:hidden}
			summary{display:flex;justify-content:space-between;gap:12px;padding:12px 16px;cursor:pointer;font-weight:600}
			.count{font-weight:600;padding:2px 10px;border-radius:999px}.count.ok{background:var(--okbg);color:var(--ok)}.count.bad{background:var(--badbg);color:var(--bad)}
			h3{margin:12px 16px 6px;font-size:14px;color:var(--muted)}.scroll{overflow-x:auto;padding:0 16px 12px}
			table{border-collapse:collapse;width:100%;min-width:900px}th,td{text-align:left;vertical-align:top;padding:6px 8px;border-bottom:1px solid var(--line)}
			th{font-size:12px;color:var(--muted);font-weight:600}.mono{font-family:ui-monospace,Consolas,monospace;font-size:12.5px;word-break:break-word}
			tr.fail{background:var(--badbg)}tr.fail td:last-child{color:var(--bad);font-weight:500}
			.tag{font-size:12px;font-weight:700;padding:1px 8px;border-radius:4px}tr.pass .tag{background:var(--okbg);color:var(--ok)}tr.fail .tag{background:var(--bad);color:#fff}
			body.only-failed tr.pass,body.only-failed .rule:not(.has-fail),body.only-failed .suite:not(.has-fail){display:none}
			""";

	private static final String JS = """
			document.getElementById('only').addEventListener('change',function(e){document.body.classList.toggle('only-failed',e.target.checked);
			if(e.target.checked)document.querySelectorAll('details').forEach(function(d){if(d.querySelector('tr.fail'))d.open=true;});});
			""";
}
