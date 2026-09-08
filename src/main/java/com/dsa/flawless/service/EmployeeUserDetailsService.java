package com.dsa.flawless.service;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeUserDetailsService implements UserDetailsService {
	
	private final EmployeeRepository employeeRepository;

	@Override
	public UserDetails loadUserByUsername(String username)
			throws UsernameNotFoundException {
		
		Employee employee = employeeRepository.findByUsername(username)
				.orElseThrow(() -> {
					log.info("===== 사용자 없음 =====");
					log.info("조회한 username = {}", username);
					return new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
				});
		
		log.info("===== 사용자 찾음 =====");
		log.info("username = {}", employee.getUsername());
		log.info("password = {}", employee.getPassword());
		log.info("role = {}", employee.getRole());
		
		return new EmployeeUserDetails(employee);
	}
}
