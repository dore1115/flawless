package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// REQ-NF-H-008 개선 완료: 직책/부서 삭제 시 "소속 사원 조회"(PositionRepository.findByPosition,
// DepartmentRepository.findByDepartment)가 employee.position_id/department_id로 조회하는데
// 인덱스가 없어 Full scan이 발생하던 문제. 아래 indexes는 문서화 목적이다 — 이 프로젝트는
// hibernate.ddl-auto=validate라 Hibernate가 시작 시 컬럼 존재 여부만 검증할 뿐 인덱스를 만들거나
// 강제하지 않는다. 실제 인덱스는 container/mysql-init/schema.sql의 employee 테이블 정의에
// idx_employee_department_id / idx_employee_position_id로 추가해뒀다 — 그게 실제 DB 스키마를
// 결정하는 원본이다.
@Table(
		name="employee",
		uniqueConstraints = @UniqueConstraint(columnNames = {"username"}),
		indexes = {
				@Index(name = "idx_employee_department_id", columnList = "department_id"),
				@Index(name = "idx_employee_position_id", columnList = "position_id")
		}
)

// TODO(REQ-NF-M-003 하드 삭제 + 감사로그 부재): 아직 사원 삭제 기능 자체가 없지만,
// 나중에 추가할 때 JpaRepository.delete()/deleteById()로 바로 하드 삭제하지 말 것.
// -> deleted(boolean) 또는 status(VARCHAR: ACTIVE/DELETED) 컬럼을 추가해 소프트 삭제로 처리하고,
//    관리자의 조회/수정/삭제 행위는 별도 감사로그 테이블(누가/언제/무엇을)에 기록할 것.
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
	
	// TODO(REQ-NF-L-005/L-006 조회 지연): department/position이 둘 다 EAGER라
	// employee를 조회할 때마다(로그인 포함) 항상 두 테이블이 조인된다. 목록/상세 조회에서
	// N+1까지 겹치면 더 느려지므로 LAZY로 바꾸고, 필요한 화면에서만 fetch join으로
	// 명시적으로 가져오는 방식 검토.
	
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
