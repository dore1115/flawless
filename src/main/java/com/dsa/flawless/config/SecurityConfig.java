package com.dsa.flawless.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		
		http
				// 페이지 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login").permitAll() // login은 누구나 접근 가능
						.anyRequest() // 그 외 모든 요청
						.permitAll() //! REQ-NF-G-003으로 인하여 인가 아키텍쳐는 추후 적용
				)
				.formLogin(form -> form
						.loginPage("/login")
						.defaultSuccessUrl("/", true)
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
