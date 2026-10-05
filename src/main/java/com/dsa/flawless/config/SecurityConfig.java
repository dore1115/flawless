package com.dsa.flawless.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

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
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			// REQ-NF-S-001: 동시 로그인 허용 수를 application.yaml에서 읽음 (값이 없으면 1)
			@Value("${app.security.max-sessions:1}") int maxSessions) throws Exception {

		http
				// 페이지 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/js/**").permitAll() // 로그인 화면·정적 리소스만 예외
						.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll() // Prometheus 스크레이핑용 (9091은 호스트에 노출 안 함)
						.requestMatchers(HttpMethod.POST, "/inventory", "/inventory/*/delete").hasRole("ADMIN_IV")
						.requestMatchers(HttpMethod.POST, "/inventory/*").hasRole("ADMIN_IV")
						.anyRequest().authenticated() //! REQ-NF-G-003 해결: 로그인 필수로 전환
						//! [REQ-NF-I-002] 물류관리자(ADMIN_IV) 전용으로 제한: 품목 등록/수정/삭제 + 입고/출고.

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
						.maximumSessions(maxSessions)
						.maxSessionsPreventsLogin(false)
						.expiredUrl("/login?expired")
				);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder(
			// REQ-NF-S-001: 비밀번호 암호화 강도를 application.yaml에서 읽음 (값이 없으면 10)
			@Value("${app.security.bcrypt-strength:10}") int bcryptStrength) {
		return new BCryptPasswordEncoder(bcryptStrength);
	}

	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

}
