package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
//! REQ-NF-H-008 — 인덱스 부재
//! 1. 엔티티에 @Table(indexes = {@Index(columnList = "position_id"), @Index(columnList = "department_id")})를 추가
//! 	(문서화 목적, ddl-auto=validate라 실제로 DB를 바꾸진 않음).
//! 2. 실제 DB에 ALTER TABLE employee ADD INDEX idx_employee_position_id (position_id),
//! 	ADD INDEX idx_employee_department_id (department_id);를 직접 실행(또는 schema.sql/마이그레이션 파일로 관리)
@Table(
		name="employee",
		uniqueConstraints = @UniqueConstraint(columnNames = {"username"})
)
@Getter
@NoArgsConstructor
public class Employee extends BaseTimeEntity{
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

	@JoinColumn(name = "department_id") @ManyToOne(fetch = FetchType.EAGER)
	private Department department;

	@JoinColumn(name = "position_id") @ManyToOne(fetch = FetchType.EAGER)
	private Position position;

	@Column(nullable = false, length = 20) @Enumerated(EnumType.STRING)
	private Role role;

	@Builder
	public Employee(String username, String password, String name, String address, String email,
	                String phone, Department department, Position position, Role role) {
		this.username = username;
		this.password = password;
		this.name = name;
		this.address = address;
		this.email = email;
		this.phone = phone;
		this.department = department;
		this.position = position;
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
