package com.dsa.flawless.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
//! REQ-NF-H-004 개선 완료: @EnableMethodSecurity를 추가해서 컨트롤러의 @PreAuthorize가 실제로
//! 동작하게 만들었다. EmployeeController에 hasRole('ADMIN_HR') 체크가 걸렸다.
//!
//! REQ-NF-G-003 개선 완료: 전역 인가 아키텍처 부재 — 예전엔 anyRequest().permitAll()이라
//! 로그인 여부와 무관하게 전체 API가 열려 있었다. 로그인 화면(/login)과 정적 리소스(/js/**)만
//! permitAll로 남기고 나머지는 전부 authenticated()로 바꿔서, 최소한 "로그인은 필수"라는 전역
//! 게이트가 걸리도록 했다. 세부 권한(Role)은 이 전역 게이트 위에 각 컨트롤러의
//! @PreAuthorize(hasRole(...))가 개별적으로 얹히는 구조 — H-004(EmployeeController),
//! H-005(PositionController/DepartmentController, 2026-09-22에 목록 조회까지 클래스 레벨로 확장)는
//! 김도영 담당 범위에서 이미 적용 완료. I-002(재고 관리) 등 다른 모듈은 아직 남아 있다(담당자 확인 필요).
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		http
				// 페이지 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/js/**", "/error").permitAll() // 로그인 화면·정적 리소스·공통 오류 화면(REQ-NF-G-004)만 예외
						.anyRequest().authenticated() //! REQ-NF-G-003 해결: 로그인 필수로 전환
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
				);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new PasswordEncoder() {

			@Override
			public String encode(CharSequence rawPassword) {
				return rawPassword.toString();
			}

			@Override
			public boolean matches(CharSequence rawPassword, String encodedPassword) {
				return rawPassword.toString().equals(encodedPassword);
			}
		};
	}


}
