package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="employee")
@Getter
@Setter
public class Employee {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true, length = 50)
	private String username;
	
	@Column(nullable = false, length = 255)
	private String password;
	
	@Column(nullable = false, length = 30)
	private String name;
	
	@Column(nullable = false, length = 200)
	private String address;
	
	@Column(nullable = false, length = 100, unique = true)
	private String email;
	
	@Column(nullable = false, length = 20)
	private String phone;
	
	@JoinColumn(name = "department_id")
	private Long departmentId;
	
	@Column(name = "position_id", nullable = false)
	private Long positionId;
	
	@Column(nullable = false, length = 20) @Enumerated(EnumType.STRING)
	private Role role;
	
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	
	@Builder
	public Employee(String username, String password, String name, String address, String email,
	                String phone, Department department, Position position, Role role) {
		this.username = username;
		this.password = password;
		this.name = name;
		this.address = address;
		this.email = email;
		this.phone = phone;
		this.departmentId = department;
		this.positionId = position;
		this.role = (role != null) ? role : Role.EMPLOYEE;
	}
	
	/**
	 * REQ-F-M-004: 사원 1명에게 할당된 직책/부서를 다른 직책/부서로 변경한다.
	 */
	public void changeAssignment(Position position, Department department) {
		this.position = position;
		this.department = department;
	}
	
	/**
	 * REQ-F-M-004: 사원 1명에게 할당된 이름/이메일/연락처/주소를 다른 정보로 변경한다.
	 */
	public void updatePersonalInfo(String name, String email, String phone, String address) {
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.address = address;
	}
	
}
