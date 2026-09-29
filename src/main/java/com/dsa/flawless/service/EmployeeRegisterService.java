package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.domain.Role;
import com.dsa.flawless.dto.EmployeeRegisterForm;
import com.dsa.flawless.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 회원등록 서비스
@Service
@RequiredArgsConstructor
public class EmployeeRegisterService {

	private final EmployeeRepository employeeRepository;

	private final EntityManager em;

	public void registerEmployee(EmployeeRegisterForm form) {

		if (employeeRepository.findByUsername(form.getUsername()).isPresent()) {
			throw new IllegalStateException("이미 사용 중인 아이디입니다.");
		}
		if (employeeRepository.existsByEmail(form.getEmail())) {
			throw new IllegalStateException("이미 사용 중인 이메일입니다.");
		}

		Department department = em.getReference(Department.class, form.getDepartmentId());
		Position position = em.getReference(Position.class, form.getPositionId());

		// TODO(REQ-NF-M-001 권한 상승 방지 부재): 이 메서드를 호출하는 컨트롤러가
		// @PreAuthorize("hasRole('ADMIN_HR')")로 막혀 있어 "일반 사용자가 직접 호출"하는
		// 경로는 현재 차단돼 있지만, role 문자열 자체는 여전히 클라이언트가 보낸 값을
		// 그대로 신뢰해서 Role.valueOf()에 넘긴다. 잘못된 값이 오면 IllegalArgumentException이
		// 그대로 튀어나가 500 에러가 되므로, 허용 목록 검증 + 실패 시 사용자 메시지 처리 필요.
		// (메서드 레벨 시큐리티가 실제로 켜져 있는지 - @EnableMethodSecurity 설정 여부도 함께 점검)

		Role roleValue = Role.valueOf(form.getRole());

		Employee employee = Employee.builder()
				.username(form.getUsername())
				// TODO(REQ-NF-L-001 개선 라운드 전): 지금은 평문 저장, 나중에 encoder.encode(...)로 교체
				.password(form.getPassword())
				.name(form.getName())
				.email(form.getEmail())
				.phone(form.getPhone())
				.address(form.getAddress())
				.department(department)
				.position(position)
				.role(roleValue)
				.build();

		employeeRepository.save(employee);
	}

}
