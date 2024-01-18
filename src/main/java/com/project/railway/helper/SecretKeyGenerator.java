package com.project.railway.helper;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class SecretKeyGenerator {
	public String key() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] keyBytes = new byte[32]; // 256 bits
		secureRandom.nextBytes(keyBytes);

		String secretKey = Base64.getEncoder().encodeToString(keyBytes);
		return secretKey;
	}
}
