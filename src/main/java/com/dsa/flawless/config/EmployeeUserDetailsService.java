package com.dsa.flawless.config;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.repository.EmployeeRepository;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.security.core.userdetails.User;
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
					System.out.println("===== 사용자 없음 =====");
					System.out.println("조회한 username = " + username);
					return new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
				});
		
		System.out.println("===== 사용자 찾음 =====");
		System.out.println("username = " + employee.getUsername());
		System.out.println("password = " + employee.getPassword());
		System.out.println("role = " + employee.getRole());
		
		return new EmployeeUserDetails(employee);
	}
}
