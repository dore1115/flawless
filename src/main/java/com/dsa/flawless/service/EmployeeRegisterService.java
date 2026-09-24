package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.domain.Role;
import com.dsa.flawless.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// 회원등록 서비스
@Service
@RequiredArgsConstructor
public class EmployeeRegisterService {
	
	private final EmployeeRepository employeeRepository;
	
	private final EntityManager em;
	
	// REQ-NF-L-001 개선: 비밀번호를 BCrypt 해시로 저장하기 위해 사용
	private final PasswordEncoder passwordEncoder;
	
	public Employee registerEmployee(String username, String password, String name, String email,
	                                 String phone, String address,
	                                 Long departmentId, Long positionId, String role) {
		
		if (employeeRepository.findByUsername(username).isPresent()) {
			throw new IllegalStateException("이미 사용 중인 아이디입니다.");
		}
		if (employeeRepository.existsByEmail(email)) {
			throw new IllegalStateException("이미 사용 중인 이메일입니다.");
		}
		
		Department department = em.getReference(Department.class, departmentId);
		Position position = em.getReference(Position.class, positionId);
		
		// REQ-NF-M-001 개선: 요청자가 인사관리자인지는 EmployeeRegisterController.checkAdminHr()에서 검사한다.
		// 여기서는 클라이언트가 보낸 role 문자열을 그대로 믿지 않고, 허용된 값(ADMIN_HR/ADMIN_IV/EMPLOYEE)만 받는다.
		// 잘못된 값이 오면 500 에러 대신 사용자 안내 메시지로 처리한다.
		Role roleValue;
		try {
			roleValue = Role.valueOf(role);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalStateException("올바르지 않은 권한입니다.");
		}
		
		Employee employee = Employee.builder()
				.username(username)
				// REQ-NF-L-001 개선: 평문 저장 → BCrypt 해시 저장
				.password(passwordEncoder.encode(password))
				.name(name)
				.email(email)
				.phone(phone)
				.address(address)
				.department(department)
				.position(position)
				.role(roleValue)
				.build();
		
		return employeeRepository.save(employee);
	}
	
}