package com.project.railway.helper;

import java.sql.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtUtil {

	@Autowired
	SecretKeyGenerator keyGenerator;

	private static final long EXPIRATION_TIME_MS = 3600000;

	private static final String SECRET_KEY = new SecretKeyGenerator().key();

	private Claims parseJwtClaims(String authToken) {
		if (authToken == null) {
			return null;
		}

		try {
			return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken).getBody();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public String generateToken_for_admin(UserDetails userDetails, Date expirationDate) {
		String token = Jwts.builder().setSubject(userDetails.getUsername()).setExpiration(expirationDate) // date
				.signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
		return token;
	}

	public boolean isValidToken(String authToken) {
		if (authToken == null) {
			return false;
		}
		try {
			Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken).getBody();
			Date expirationDate = (Date) claims.getExpiration();
			return expirationDate != null && !expirationDate.before(new Date(System.currentTimeMillis()));
		} catch (ExpiredJwtException expiredEx) {
			return false;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

}
