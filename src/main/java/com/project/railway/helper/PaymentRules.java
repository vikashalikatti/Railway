package com.project.railway.helper;

import java.util.Locale;

/**
 * Test payment gateway rules (no real money moves). Plain Java so it can be unit-tested.
 *
 * Cards: any number that passes the Luhn check is approved after the OTP 123456, except the test
 * card 4000 0000 0000 0002, which is always declined. UPI: any id like name@bank is approved, except
 * failure@upi. Net banking: the bank page has Approve and Decline buttons.
 * Only the card's last 4 digits are ever kept; the CVV is checked for format and thrown away.
 */
public final class PaymentRules {

	private PaymentRules() {
	}

	public static final String TEST_OTP = "123456";
	public static final String DECLINED_CARD = "4000000000000002";
	public static final String FAILING_UPI = "failure@upi";

	public static String digits(String s) {
		return s == null ? "" : s.replaceAll("[\\s-]", "");
	}

	public static boolean luhn(String number) {
		String n = digits(number);
		if (!n.matches("\\d{12,19}")) {
			return false;
		}
		int sum = 0;
		boolean twice = false;
		for (int i = n.length() - 1; i >= 0; i--) {
			int d = n.charAt(i) - '0';
			if (twice) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			sum += d;
			twice = !twice;
		}
		return sum % 10 == 0;
	}

	public static String brand(String number) {
		String n = digits(number);
		if (n.startsWith("4")) {
			return "Visa";
		}
		if (n.matches("5[1-5].*") || n.matches("2[2-7].*")) {
			return "Mastercard";
		}
		if (n.matches("(60|65|81|82|508).*")) {
			return "RuPay";
		}
		if (n.matches("3[47].*")) {
			return "Amex";
		}
		return "Card";
	}

	/** Problem with the card details, or null */
	public static String cardProblem(String number, String expiry, String cvv, java.time.YearMonth now) {
		if (!luhn(number)) {
			return "Enter a valid card number.";
		}
		String e = expiry == null ? "" : expiry.trim();
		if (!e.matches("(0[1-9]|1[0-2])/\\d{2}")) {
			return "Enter the expiry as MM/YY.";
		}
		java.time.YearMonth exp = java.time.YearMonth.of(2000 + Integer.parseInt(e.substring(3)), Integer.parseInt(e.substring(0, 2)));
		if (exp.isBefore(now)) {
			return "This card has expired.";
		}
		if (cvv == null || !cvv.trim().matches("\\d{3,4}")) {
			return "Enter the 3 or 4 digit CVV.";
		}
		return null;
	}

	public static boolean validUpi(String vpa) {
		return vpa != null && vpa.trim().toLowerCase(Locale.ROOT).matches("[a-z0-9._-]{2,64}@[a-z]{2,32}");
	}

	public static String mask(String number) {
		String n = digits(number);
		return "•••• " + n.substring(Math.max(0, n.length() - 4));
	}
}
