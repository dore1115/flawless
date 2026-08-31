package com.dsa.flawless.config;

import com.dsa.flawless.domain.Employee;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class EmployeeUserDetails implements UserDetails {
	private  final Employee employee;
	public EmployeeUserDetails(Employee employee) {this.employee = employee; }
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(employee.getRole().name()));
	}
	
	@Override
	public @Nullable String getPassword() {
		return employee.getPassword();
	}
	
	@Override
	public String getUsername() {
		return employee.getUsername();
	}
}
