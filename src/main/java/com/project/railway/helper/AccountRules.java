package com.project.railway.helper;

import java.util.regex.Pattern;

import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side checks for account details. They match the checks in the React app
 * (frontend/src/validate.js), so a request sent straight to the API (Postman, a script)
 * is held to the same rules. Each method returns an error message, or null when the value is fine.
 */
public final class AccountRules {

	private AccountRules() {
	}

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
	private static final Pattern PERSON_NAME = Pattern.compile("^[A-Za-z][A-Za-z .'-]*$");
	private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
	private static final Pattern ADMIN_NAME = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");

	public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

	public static String email(String v) {
		String s = v == null ? "" : v.trim();
		if (s.isEmpty()) {
			return "Enter your email address";
		}
		if (s.length() > 100 || !EMAIL.matcher(s).matches()) {
			return "Enter a valid email address";
		}
		return null;
	}

	public static String personName(String v) {
		String s = v == null ? "" : v.trim();
		if (s.length() < 2 || s.length() > 50 || !PERSON_NAME.matcher(s).matches()) {
			return "Name must be 2 to 50 letters";
		}
		return null;
	}

	public static String mobile(long v) {
		return MOBILE.matcher(String.valueOf(v)).matches() ? null : "Mobile number must be 10 digits starting with 6 to 9";
	}

	/** At least 8 characters, a letter and a number, no spaces, at most 64 (BCrypt reads only 72 bytes) */
	public static String newPassword(String v) {
		String s = v == null ? "" : v;
		if (s.length() < 8 || s.length() > 64) {
			return "Password must be 8 to 64 characters";
		}
		if (!s.matches(".*[A-Za-z].*") || !s.matches(".*\\d.*")) {
			return "Password needs at least one letter and one number";
		}
		if (s.matches(".*\\s.*")) {
			return "Password can't contain spaces";
		}
		return null;
	}

	public static String age(int v) {
		return v >= 1 && v <= 120 ? null : "Age must be between 1 and 120";
	}

	public static String address(String v) {
		String s = v == null ? "" : v.trim();
		return s.length() >= 5 && s.length() <= 200 ? null : "Address must be 5 to 200 characters";
	}

	public static String gender(String v) {
		String s = v == null ? "" : v.trim();
		return s.isEmpty() || s.length() > 20 ? "Choose a gender" : null;
	}

	public static String adminName(String v) {
		String s = v == null ? "" : v.trim();
		return ADMIN_NAME.matcher(s).matches() ? null
				: "Admin name must be 3 to 30 letters, numbers, dots or underscores";
	}

	/** JPG, PNG or WebP, checked by the file's first bytes (the name and type can be faked) */
	public static String photo(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return "Add a profile photo";
		}
		if (file.getSize() > MAX_PHOTO_BYTES) {
			return "Photo must be 5 MB or smaller";
		}
		try {
			byte[] head = new byte[12];
			int n;
			try (var in = file.getInputStream()) {
				n = in.readNBytes(head, 0, head.length);
			}
			if (isJpeg(head, n) || isPng(head, n) || isWebp(head, n)) {
				return null;
			}
		} catch (java.io.IOException e) {
			return "The photo couldn't be read";
		}
		return "Photo must be a JPG, PNG or WebP image";
	}

	private static boolean isJpeg(byte[] b, int n) {
		return n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] b, int n) {
		return n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
	}

	private static boolean isWebp(byte[] b, int n) {
		return n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
				&& b[10] == 'B' && b[11] == 'P';
	}
}
