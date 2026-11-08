package com.project.railway.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Customer;
import com.project.railway.repository.Admin_Repository;
import com.project.railway.repository.Customer_Repository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Creates and checks log-in tokens.
 *
 * A token is accepted only when:
 *  - it was signed with this app's secret (JWT_SECRET) and hasn't expired,
 *  - it is a log-in token (password-reset tokens only work for resetting the password),
 *  - it hasn't been logged out, and
 *  - the account still exists and its token version matches (raised by "log out everywhere"
 *    and by a password reset, which ends every other session).
 */
@Component
public class JwtUtil {

	private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

	public static final long LOGIN_TTL_MS = 60 * 60 * 1000L; // 1 hour
	public static final long RESET_TTL_MS = 10 * 60 * 1000L; // 10 minutes

	public static final String PURPOSE_LOGIN = "login";
	public static final String PURPOSE_RESET = "reset";

	/** What a valid token says: who, which role, what it may be used for */
	public record TokenInfo(String subject, String role, String purpose, String id, Date expiresAt) {
	}

	private final SecretKey key;

	/** Logged-out tokens (id -> expiry time), kept until they would have expired anyway */
	private final Map<String, Long> loggedOut = new ConcurrentHashMap<>();

	@Autowired
	@Lazy
	private Admin_Repository adminRepository;

	@Autowired
	@Lazy
	private Customer_Repository customerRepository;

	public JwtUtil(@Value("${app.jwt.secret:}") String secret) {
		String s = secret == null ? "" : secret.trim();
		if (s.isEmpty()) {
			byte[] random = new byte[64];
			new SecureRandom().nextBytes(random);
			s = Base64.getEncoder().encodeToString(random);
			log.warn("JWT_SECRET is not set, so a random one is used: everyone is logged out whenever the app restarts. "
					+ "Set the JWT_SECRET environment variable to a long random value (at least 32 characters).");
		} else if (s.length() < 32) {
			log.warn("JWT_SECRET is shorter than 32 characters. Use a longer random value.");
		}
		// HS512 needs a 512-bit key: derive it from the secret, whatever its length
		this.key = Keys.hmacShaKeyFor(sha512(s));
	}

	/* ------------------------------------------------------------------ issuing */

	public String generateToken_for_admin(Admin admin, Date expirationDate) {
		return build(admin.getName(), "admin", PURPOSE_LOGIN, version(admin.getTokenVersion()), expirationDate);
	}

	public String generateToken_for_customer(Customer customer, Date expirationDate) {
		return build(customer.getEmail(), "customer", PURPOSE_LOGIN, version(customer.getTokenVersion()),
				expirationDate);
	}

	/** Short-lived token that can only be used to set a new password */
	public String generateResetToken(Customer customer) {
		return build(customer.getEmail(), "customer", PURPOSE_RESET, version(customer.getTokenVersion()),
				new Date(System.currentTimeMillis() + RESET_TTL_MS));
	}

	private String build(String subject, String role, String purpose, int version, Date expiresAt) {
		return Jwts.builder().setSubject(subject + "|" + role).setId(UUID.randomUUID().toString())
				.setIssuedAt(new Date()).setExpiration(expiresAt).claim("role", role).claim("purpose", purpose)
				.claim("ver", version).signWith(key, SignatureAlgorithm.HS512).compact();
	}

	/* ----------------------------------------------------------------- checking */

	/** The token's details if it is valid for this purpose, otherwise null */
	public TokenInfo verify(String token, String purpose) {
		Claims claims = parse(token);
		if (claims == null || claims.getSubject() == null || claims.getExpiration() == null
				|| claims.getExpiration().before(new Date())) {
			return null;
		}
		String tokenPurpose = claims.get("purpose", String.class);
		if (!purpose.equals(tokenPurpose == null ? PURPOSE_LOGIN : tokenPurpose)) {
			return null;
		}
		if (claims.getId() != null && loggedOut.containsKey(claims.getId())) {
			return null;
		}
		String[] parts = claims.getSubject().split("\\|", 2);
		if (parts.length != 2) {
			return null;
		}
		Number ver = claims.get("ver", Number.class);
		int tokenVersion = ver == null ? 0 : ver.intValue();
		if ("admin".equals(parts[1])) {
			Admin admin = adminRepository.findByName(parts[0]);
			if (admin == null || version(admin.getTokenVersion()) != tokenVersion) {
				return null;
			}
		} else if ("customer".equals(parts[1])) {
			Customer customer = customerRepository.findByEmail(parts[0]);
			if (customer == null || version(customer.getTokenVersion()) != tokenVersion) {
				return null;
			}
		} else {
			return null;
		}
		return new TokenInfo(parts[0], parts[1], tokenPurpose == null ? PURPOSE_LOGIN : tokenPurpose, claims.getId(),
				claims.getExpiration());
	}

	/** True for a valid log-in token (any role) */
	public boolean isValidToken(String authToken) {
		return verify(authToken, PURPOSE_LOGIN) != null;
	}

	/** [email or admin name, role] of a valid log-in token; null if the token is invalid, expired or logged out */
	public String[] getSubject(String authToken) {
		TokenInfo info = verify(authToken, PURPOSE_LOGIN);
		return info == null ? null : new String[] { info.subject(), info.role() };
	}

	/** Ends this one token (log out on this device) */
	public void logout(String token) {
		Claims claims = parse(token);
		if (claims == null || claims.getId() == null || claims.getExpiration() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		loggedOut.values().removeIf(expiry -> expiry < now); // forget tokens that have expired anyway
		loggedOut.put(claims.getId(), claims.getExpiration().getTime());
	}

	private Claims parse(String token) {
		if (token == null || token.isBlank() || token.length() > 4096) {
			return null;
		}
		try {
			return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token.trim()).getBody();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public static int version(Integer v) {
		return v == null ? 0 : v;
	}

	private static byte[] sha512(String s) {
		try {
			return MessageDigest.getInstance("SHA-512").digest(s.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
