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
		// TODO(REQ-NF-L-005 로그인 응답 지연): Employee.department/position이 FetchType.EAGER라
		// 로그인 인증 한 번에 employee 조회 + department/position 조인까지 매번 딸려온다.
		// 인증 시점엔 role/password만 있으면 되므로, LAZY로 바꾸거나 인증 전용 조회 쿼리
		// (예: username/password/role만 select)를 따로 두는 걸 검토. 100명 동시 로그인 시
		// p95 응답시간 측정해서 개선 전/후 비교할 것.
		Employee employee = employeeRepository.findByUsername(username)
				.orElseThrow(() -> {
					log.info("===== 사용자 없음 =====");
					log.info("조회한 username = {}", username);
					return new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
				});
		// REQ-NF-L-001 부가 결함 처리: 비밀번호 값을 로그에 남기던 코드를 제거함.
		// (요청 실패 로그에도 입력 비밀번호 원문을 남기지 않는다 — REQ-F-W-005 비고와 동일한 원칙)
		log.info("===== 사용자 찾음 =====");
		log.info("username = {}", employee.getUsername());
		log.info("role = {}", employee.getRole());
		
		return new EmployeeUserDetails(employee);
	}
}