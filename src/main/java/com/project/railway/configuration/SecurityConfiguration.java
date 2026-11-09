package com.project.railway.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.project.railway.helper.JwtUtil;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Who may call what:
 *  - log-in, sign-up, OTP and password-reset endpoints, and live station search: anyone
 *  - everything else under /admin: a logged-in admin
 *  - everything else under /customer: a logged-in passenger
 *  - anything else: nobody (API docs only when app.docs.public=true)
 *
 * No server sessions or cookies: every request carries its token, so CSRF doesn't apply.
 * Refusals come back as JSON in the usual {status, message} shape.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
	private String allowedOrigins;

	@Value("${app.docs.public:false}")
	private boolean docsPublic;

	@Bean
	BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity security, JwtUtil jwtUtil) throws Exception {
		security.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(h -> h
						.frameOptions(f -> f.deny())
						.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
				.addFilterBefore(new JwtAuthFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) -> json(res, HttpStatus.UNAUTHORIZED,
								"Your session has ended. Log in again."))
						.accessDeniedHandler((req, res, ex) -> json(res, HttpStatus.FORBIDDEN,
								"You don't have permission to do that.")))
				.authorizeHttpRequests(request -> {
					request.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
							.requestMatchers("/error").permitAll()
							.requestMatchers("/actuator/health").permitAll()
							// open: getting in, and live station suggestions
							.requestMatchers(HttpMethod.POST, "/admin/login", "/admin/create").permitAll()
							.requestMatchers(HttpMethod.POST, "/customer/signup", "/customer/login",
									"/customer/verify_otp", "/customer/resend_otp", "/customer/forgot_passowrd",
									"/customer/forgot-otp/**", "/customer/reset-password")
							.permitAll()
							.requestMatchers(HttpMethod.GET, "/customer/stations").permitAll()
							// chart & vacancy: berths only, no personal details
							.requestMatchers(HttpMethod.GET, "/customer/chart/**").permitAll()
							// PNR status (no names) and running status
							.requestMatchers(HttpMethod.GET, "/customer/pnr-status/**", "/customer/running/**").permitAll()
							// everything else needs the right role
							.requestMatchers("/admin/**").hasRole("ADMIN")
							.requestMatchers("/customer/**").hasRole("CUSTOMER");
					if (docsPublic) {
						request.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
					}
					request.anyRequest().denyAll();
				});
		return security.build();
	}

	/** Only the app's own pages may call the API from a browser (app.cors.allowed-origins) */
	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.toList());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Bearer", "Authorization", "Content-Type"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration)
			throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	private static void json(HttpServletResponse res, HttpStatus status, String message) throws java.io.IOException {
		res.setStatus(status.value());
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding("UTF-8");
		res.getWriter().write("{\"status\":" + status.value() + ",\"message\":\"" + message.replace("\"", "\\\"")
				+ "\",\"data\":null,\"data2\":null}");
	}
}
