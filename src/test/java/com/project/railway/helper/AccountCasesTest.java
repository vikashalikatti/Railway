package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.project.railway.testsupport.CaseTable;

/** Sign-up and profile checks (null = accepted), and log-in lockouts */
class AccountCasesTest {

	private static final String BAD_EMAIL = "Enter a valid email address";
	private static final String NAME = "Name must be 2 to 50 letters";

	@Test
	void signUpFields() {
		CaseTable t = CaseTable.of("Accounts", "Sign-up fields: what is accepted and the message otherwise");
		Object[][] emails = { { "vikas@example.com", null }, { " vikas@example.com ", null }, { "a.b+rail@mail.co.in", null },
				{ "", "Enter your email address" }, { null, "Enter your email address" }, { "vikas", BAD_EMAIL }, { "vikas@example", BAD_EMAIL },
				{ "vikas@example.c", BAD_EMAIL }, { "vi kas@example.com", BAD_EMAIL }, { "a".repeat(95) + "@x.com", BAD_EMAIL } };
		for (Object[] c : emails) {
			String v = (String) c[0];
			t.check("Email " + label(v), in("email", v), c[1], () -> AccountRules.email(v));
		}
		Object[][] names = { { "Vikas", null }, { "Vikas L Halikatti", null }, { "D'Souza", null }, { "Anne-Marie", null }, { "V", NAME },
				{ "Vikas2", NAME }, { "-Vikas", NAME }, { "a".repeat(51), NAME }, { null, NAME } };
		for (Object[] c : names) {
			String v = (String) c[0];
			t.check("Name " + label(v), in("name", v), c[1], () -> AccountRules.personName(v));
		}
		Object[][] mobiles = { { 9876543210L, null }, { 6000000000L, null }, { 5876543210L, "Mobile number must be 10 digits starting with 6 to 9" },
				{ 987654321L, "Mobile number must be 10 digits starting with 6 to 9" },
				{ 98765432101L, "Mobile number must be 10 digits starting with 6 to 9" } };
		for (Object[] c : mobiles) {
			long v = (Long) c[0];
			t.check("Mobile " + v, in("mobile", v), c[1], () -> AccountRules.mobile(v));
		}
		Object[][] passwords = { { "train2026", null }, { "Abcdefg1", null }, { "a1" + "x".repeat(62), null },
				{ "abc12", "Password must be 8 to 64 characters" }, { "a1" + "x".repeat(63), "Password must be 8 to 64 characters" },
				{ "abcdefgh", "Password needs at least one letter and one number" }, { "12345678", "Password needs at least one letter and one number" },
				{ "train 2026", "Password can't contain spaces" }, { null, "Password must be 8 to 64 characters" } };
		for (Object[] c : passwords) {
			String v = (String) c[0];
			t.check("Password " + (v == null ? "(missing)" : v.length() > 20 ? v.length() + " characters" : "\"" + v + "\""), in("password", v), c[1],
					() -> AccountRules.newPassword(v));
		}
		int[] ages = { 0, 1, 120, 121 };
		String[] ageMsg = { "Age must be between 1 and 120", null, null, "Age must be between 1 and 120" };
		for (int i = 0; i < ages.length; i++) {
			int a = ages[i];
			t.check("Age " + a, in("age", a), ageMsg[i], () -> AccountRules.age(a));
		}
		t.check("Address too short", in("address", "abc"), "Address must be 5 to 200 characters", () -> AccountRules.address("abc"));
		t.check("Address fine", in("address", "MG Road, Bengaluru"), null, () -> AccountRules.address("MG Road, Bengaluru"));
		t.check("Address too long", in("address", "201 characters"), "Address must be 5 to 200 characters", () -> AccountRules.address("x".repeat(201)));
		t.check("Gender given", in("gender", "Female"), null, () -> AccountRules.gender("Female"));
		t.check("Gender missing", in("gender", " "), "Choose a gender", () -> AccountRules.gender(" "));
		Object[][] admins = { { "admin", null }, { "vikas_99.ops", null }, { "ab", "Admin name must be 3 to 30 letters, numbers, dots or underscores" },
				{ "admin user", "Admin name must be 3 to 30 letters, numbers, dots or underscores" },
				{ "a".repeat(31), "Admin name must be 3 to 30 letters, numbers, dots or underscores" } };
		for (Object[] c : admins) {
			String v = (String) c[0];
			t.check("Admin name " + label(v), in("admin name", v), c[1], () -> AccountRules.adminName(v));
		}
		t.done();
	}

	private static String label(String v) {
		return v == null ? "(missing)" : v.length() > 30 ? "of " + v.length() + " characters" : "\"" + v + "\"";
	}

	@Test
	void profilePhotos() {
		CaseTable t = CaseTable.of("Accounts", "Profile photo: JPG, PNG or WebP by its first bytes, at most 5 MB");
		byte[] jpg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0 };
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0 };
		byte[] webp = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };
		byte[] gif = { 'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0 };
		t.check("JPG", in("file", "photo.jpg", "first bytes", "FF D8 FF"), null, () -> AccountRules.photo(new MockMultipartFile("p", "photo.jpg", "image/jpeg", jpg)));
		t.check("PNG", in("file", "photo.png", "first bytes", "89 PNG"), null, () -> AccountRules.photo(new MockMultipartFile("p", "photo.png", "image/png", png)));
		t.check("WebP", in("file", "photo.webp", "first bytes", "RIFF....WEBP"), null,
				() -> AccountRules.photo(new MockMultipartFile("p", "photo.webp", "image/webp", webp)));
		t.check("GIF is refused", in("file", "photo.gif", "first bytes", "GIF89a"), "Photo must be a JPG, PNG or WebP image",
				() -> AccountRules.photo(new MockMultipartFile("p", "photo.gif", "image/gif", gif)));
		t.check("A GIF renamed to .jpg is still refused", in("file", "photo.jpg", "first bytes", "GIF89a"), "Photo must be a JPG, PNG or WebP image",
				() -> AccountRules.photo(new MockMultipartFile("p", "photo.jpg", "image/jpeg", gif)));
		t.check("Empty file", in("file", "empty.jpg", "size", 0), "Add a profile photo",
				() -> AccountRules.photo(new MockMultipartFile("p", "empty.jpg", "image/jpeg", new byte[0])));
		t.check("No file", in("file", null), "Add a profile photo", () -> AccountRules.photo(null));
		t.check("Over 5 MB", in("file", "big.jpg", "size", "5 MB + 1 byte"), "Photo must be 5 MB or smaller", () -> {
			byte[] big = new byte[(int) AccountRules.MAX_PHOTO_BYTES + 1];
			System.arraycopy(jpg, 0, big, 0, jpg.length);
			return AccountRules.photo(new MockMultipartFile("p", "big.jpg", "image/jpeg", big));
		});
		t.check("Exactly 5 MB is fine", in("file", "max.jpg", "size", "5 MB"), null, () -> {
			byte[] max = new byte[(int) AccountRules.MAX_PHOTO_BYTES];
			System.arraycopy(jpg, 0, max, 0, jpg.length);
			return AccountRules.photo(new MockMultipartFile("p", "max.jpg", "image/jpeg", max));
		});
		t.done();
	}

	@Test
	void logInLockout() {
		CaseTable t = CaseTable.of("Accounts", "Log-in lockout after wrong passwords, and OTP email spacing");
		t.check("4 wrong passwords: not locked", in("account", "a@x.com", "wrong tries", 4), 0L, () -> {
			LoginAttemptService s = new LoginAttemptService();
			for (int i = 0; i < 4; i++) {
				s.failed("a@x.com");
			}
			return s.secondsLocked("a@x.com");
		});
		t.checkThat("5 wrong passwords: locked for 15 minutes", in("account", "a@x.com", "wrong tries", 5), "about 900 seconds", () -> {
			LoginAttemptService s = new LoginAttemptService();
			for (int i = 0; i < 5; i++) {
				s.failed("a@x.com");
			}
			return s.secondsLocked("a@x.com");
		}, secs -> secs >= 895 && secs <= 900);
		t.checkThat("Account names ignore case and spaces", in("tries on", "A@X.com ", "checked", "a@x.com"), "locked", () -> {
			LoginAttemptService s = new LoginAttemptService();
			for (int i = 0; i < 5; i++) {
				s.failed("A@X.com ");
			}
			return s.secondsLocked("a@x.com");
		}, secs -> secs > 0);
		t.check("A right password clears the count", in("tries", "4 wrong, 1 right, 4 wrong"), 0L, () -> {
			LoginAttemptService s = new LoginAttemptService();
			for (int i = 0; i < 4; i++) {
				s.failed("b@x.com");
			}
			s.succeeded("b@x.com");
			for (int i = 0; i < 4; i++) {
				s.failed("b@x.com");
			}
			return s.secondsLocked("b@x.com");
		});
		t.check("Another account isn't locked", in("locked", "a@x.com", "checked", "c@x.com"), 0L, () -> {
			LoginAttemptService s = new LoginAttemptService();
			for (int i = 0; i < 5; i++) {
				s.failed("a@x.com");
			}
			return s.secondsLocked("c@x.com");
		});
		t.check("First OTP email goes now", in("email", "a@x.com"), 0L, () -> new LoginAttemptService().emailCooldown("a@x.com"));
		t.checkThat("Second OTP email within a minute must wait", in("email", "a@x.com", "sent", "twice"), "1 to 60 seconds", () -> {
			LoginAttemptService s = new LoginAttemptService();
			s.emailCooldown("a@x.com");
			return s.emailCooldown("a@x.com");
		}, secs -> secs >= 1 && secs <= 60);
		t.check("Another address isn't held up", in("emails", "a@x.com, then b@x.com"), 0L, () -> {
			LoginAttemptService s = new LoginAttemptService();
			s.emailCooldown("a@x.com");
			return s.emailCooldown("b@x.com");
		});
		Object[][] waits = { { 30L, "Too many attempts. Try again in a minute." }, { 60L, "Too many attempts. Try again in a minute." },
				{ 61L, "Too many attempts. Try again in 2 minutes." }, { 720L, "Too many attempts. Try again in 12 minutes." } };
		for (Object[] c : waits) {
			long secs = (Long) c[0];
			t.check("Message for " + secs + " seconds", in("seconds", secs), c[1], () -> LoginAttemptService.waitMessage(secs));
		}
		t.check("Lockout limits", in("rule", "per account / per address / minutes"), List.of(5, 30, 15L),
				() -> List.of(LoginAttemptService.MAX_PER_ACCOUNT, LoginAttemptService.MAX_PER_IP, LoginAttemptService.LOCK.toMinutes()));
		t.done();
	}
}
