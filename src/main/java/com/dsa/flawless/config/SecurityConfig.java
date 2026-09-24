package com.dsa.flawless.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
//! REQ-NF-H-004, REQ-NF-H-005 (인가(Role) 부재)
//! 1. @EnableMethodSecurity를 추가해서 @PreAuthorize가 실제로 동작하게 만듭니다.
//! 2. PositionController, DepartmentController 클래스 위에 @PreAuthorize("hasRole('ADMIN_HR')")를 붙이고,
//! EmployeeController.updateAssignment() (또는 EmployeeAssignmentService.updateEmployeeInfo())에도 같은 걸 붙입니다
//! — 이미 EmployeeRegisterController에 있는 패턴을 그대로 따라가면 됩니다.
public class SecurityConfig {
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		
		http
				// 페이지 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						.anyRequest().permitAll() //! REQ-NF-G-003으로 인하여 인가 아키텍쳐는 추후 적용
				)
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.defaultSuccessUrl("/", true)
						.failureUrl("/login")
				)
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login")
				)
				.sessionManagement(session -> session
						.maximumSessions(1)
						.maxSessionsPreventsLogin(false)
						.expiredUrl("/login?expired")
				);
		return http.build();
	}
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}
	
}