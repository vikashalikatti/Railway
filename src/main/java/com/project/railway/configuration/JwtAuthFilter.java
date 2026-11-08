package com.project.railway.configuration;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.project.railway.helper.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the log-in token from the "Bearer" header (what the app sends) or a standard
 * "Authorization: Bearer ..." header, and tells Spring Security who is calling and in which
 * role (ROLE_ADMIN or ROLE_CUSTOMER). A missing or invalid token leaves the caller anonymous;
 * SecurityConfiguration then decides whether the endpoint allows that.
 *
 * Created by SecurityConfiguration (not a @Component) so it runs once, inside Spring Security.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;

	public JwtAuthFilter(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = tokenOf(request);
		if (token != null) {
			JwtUtil.TokenInfo info = jwtUtil.verify(token, JwtUtil.PURPOSE_LOGIN);
			if (info != null) {
				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(info.subject(), null,
						List.of(new SimpleGrantedAuthority("ROLE_" + info.role().toUpperCase(Locale.ROOT))));
				SecurityContextHolder.getContext().setAuthentication(auth);
			}
		}
		chain.doFilter(request, response);
	}

	static String tokenOf(HttpServletRequest request) {
		String token = request.getHeader("Bearer");
		if (token == null || token.isBlank()) {
			String auth = request.getHeader("Authorization");
			if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
				token = auth.substring(7);
			}
		}
		return token == null || token.isBlank() ? null : token.trim();
	}
}
