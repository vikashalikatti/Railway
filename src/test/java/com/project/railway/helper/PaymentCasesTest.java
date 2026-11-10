package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.project.railway.testsupport.CaseTable;

/** Test payment gateway: card numbers, expiry, CVV, UPI ids, masking */
class PaymentCasesTest {

	@Test
	void cardNumbers() {
		CaseTable t = CaseTable.of("Payments", "Card number check (Luhn) and card brand");
		Object[][] luhn = { { "Test Visa", "4111111111111111", true }, { "With spaces", "4111 1111 1111 1111", true },
				{ "With dashes", "4111-1111-1111-1111", true }, { "Declined test card is still a valid number", "4000000000000002", true },
				{ "Mastercard", "5500000000000004", true }, { "Amex, 15 digits", "378282246310005", true },
				{ "Last digit wrong", "4111111111111112", false }, { "11 digits: too short", "41111111111", false },
				{ "20 digits: too long", "41111111111111111111", false }, { "Letters", "4111abcd11111111", false },
				{ "Empty", "", false }, { "Missing", null, false } };
		for (Object[] c : luhn) {
			String n = (String) c[1];
			t.check((String) c[0], in("number", n), c[2], () -> PaymentRules.luhn(n));
		}
		Object[][] brand = { { "4111111111111111", "Visa" }, { "5500000000000004", "Mastercard" }, { "2221000000000009", "Mastercard" },
				{ "6070000000000000", "RuPay" }, { "6521000000000000", "RuPay" }, { "5081000000000000", "RuPay" },
				{ "378282246310005", "Amex" }, { "9999000000000000", "Card" } };
		for (Object[] c : brand) {
			String n = (String) c[0];
			t.check("Brand of " + n.substring(0, 4) + "...", in("number", n), c[1], () -> PaymentRules.brand(n));
		}
		t.done();
	}

	@Test
	void cardDetails() {
		CaseTable t = CaseTable.of("Payments", "Card details: number, expiry MM/YY, CVV (today = Nov 2026)");
		YearMonth now = YearMonth.of(2026, 11);
		Object[][] cases = { { "All good", "4111111111111111", "12/28", "123", null },
				{ "Expires this month: still fine", "4111111111111111", "11/26", "123", null },
				{ "4-digit CVV", "4111111111111111", "12/28", "1234", null },
				{ "Bad number", "4111111111111112", "12/28", "123", "Enter a valid card number." },
				{ "Expired last month", "4111111111111111", "10/26", "123", "This card has expired." },
				{ "Month 13", "4111111111111111", "13/28", "123", "Enter the expiry as MM/YY." },
				{ "Month without 0", "4111111111111111", "1/28", "123", "Enter the expiry as MM/YY." },
				{ "Year with 4 digits", "4111111111111111", "12/2028", "123", "Enter the expiry as MM/YY." },
				{ "No expiry", "4111111111111111", null, "123", "Enter the expiry as MM/YY." },
				{ "2-digit CVV", "4111111111111111", "12/28", "12", "Enter the 3 or 4 digit CVV." },
				{ "Letters in CVV", "4111111111111111", "12/28", "12a", "Enter the 3 or 4 digit CVV." },
				{ "No CVV", "4111111111111111", "12/28", null, "Enter the 3 or 4 digit CVV." } };
		for (Object[] c : cases) {
			String n = (String) c[1];
			String exp = (String) c[2];
			String cvv = (String) c[3];
			t.check((String) c[0], in("number", n, "expiry", exp, "cvv", cvv), c[4], () -> PaymentRules.cardProblem(n, exp, cvv, now));
		}
		t.done();
	}

	@Test
	void upiAndMasking() {
		CaseTable t = CaseTable.of("Payments", "UPI ids and masked card numbers");
		Object[][] upi = { { "ravi@okicici", true }, { "RAVI.K@ybl", true }, { " ravi_99@paytm ", true }, { "failure@upi", true },
				{ "r@ybl", false }, { "ravi@", false }, { "@ybl", false }, { "ravi@ok1", false }, { "ravi ybl", false }, { null, false } };
		for (Object[] c : upi) {
			String v = (String) c[0];
			t.check("UPI id " + CaseTableShow.quote(v), in("upi id", v), c[1], () -> PaymentRules.validUpi(v));
		}
		t.check("Mask a card", in("number", "4111 1111 1111 1111"), "•••• 1111", () -> PaymentRules.mask("4111 1111 1111 1111"));
		t.check("Mask a short number", in("number", "12"), "•••• 12", () -> PaymentRules.mask("12"));
		t.check("Digits only", in("text", "4111-1111 1111"), "411111111111", () -> PaymentRules.digits("4111-1111 1111"));
		t.check("Digits of nothing", in("text", null), "", () -> PaymentRules.digits(null));
		t.done();
	}

	/** Shows null as null and text in quotes, for case names */
	private static final class CaseTableShow {
		static String quote(String s) {
			return s == null ? "(missing)" : "\"" + s + "\"";
		}
	}
}
