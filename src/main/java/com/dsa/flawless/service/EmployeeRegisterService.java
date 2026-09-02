package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.domain.Role;
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
		Role roleValue = Role.valueOf(role);
		
		Employee employee = Employee.builder()
				.username(username)
				// TODO(REQ-NF-L-001 개선 라운드 전): 지금은 평문 저장, 나중에 encoder.encode(...)로 교체
				.password(password)
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
