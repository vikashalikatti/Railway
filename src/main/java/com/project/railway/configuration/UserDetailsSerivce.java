package com.project.railway.configuration;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.project.railway.dto.Admin;
import com.project.railway.repository.Admin_Repository;

@Service
public class UserDetailsSerivce implements UserDetailsService {

	private Admin_Repository admin_Repository;

	public UserDetailsSerivce(Admin_Repository admin_Repository) {
		super();
		this.admin_Repository = admin_Repository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

		Admin admin = admin_Repository.findByName(email);
		if (admin != null) {
			return new User(admin.getName(), admin.getPassword(),
					Collections.singletonList(new SimpleGrantedAuthority("admin")));
		}

		throw new UsernameNotFoundException("User not found: " + email);
	}
}
