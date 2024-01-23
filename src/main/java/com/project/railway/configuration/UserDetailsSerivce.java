package com.project.railway.configuration;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Customer;
import com.project.railway.repository.Admin_Repository;
import com.project.railway.repository.Customer_Repository;

@Service
public class UserDetailsSerivce implements UserDetailsService {

	private Admin_Repository admin_Repository;

	private Customer_Repository customer_Repository;

	public UserDetailsSerivce(Admin_Repository admin_Repository, Customer_Repository customer_Repository) {
		super();
		this.admin_Repository = admin_Repository;
		this.customer_Repository = customer_Repository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

		Admin admin = admin_Repository.findByName(email);
		if (admin != null) {
			return new User(admin.getName(), admin.getPassword(),
					Collections.singletonList(new SimpleGrantedAuthority("admin")));
		}

		Customer customer = customer_Repository.findByEmail(email);
		if (customer != null) {
			return new User(customer.getEmail(), customer.getPassword(),
					Collections.singletonList(new SimpleGrantedAuthority("customer")));
		}
		throw new UsernameNotFoundException("User not found: " + email);
	}
}