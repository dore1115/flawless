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

	// REQ-NF-H-009 개선 완료: 동시성 문제(Lost Update).
	// updateEmployeeInfo()가 낙관적 락 없이 곧바로 save()를 호출해, 두 요청이 거의 동시에 같은
	// 사원 행을 수정하면 나중에 커밋되는 쪽이 먼저 커밋된 변경 내용을 그대로 덮어써버리는 문제가
	// 있었다. @Version을 추가하면 Hibernate가 저장(UPDATE) 시점마다
	// "UPDATE employee SET ... , version = version+1 WHERE id=? AND version=?" 형태로
	// 자신이 읽은 시점의 version 값을 WHERE 조건에 함께 걸고, 그 사이 다른 트랜잭션이 먼저
	// 커밋해 version이 바뀌었으면 UPDATE 대상 행이 0건이 되어
	// OptimisticLockingFailureException을 던진다 — 이를
	// {@link com.dsa.flawless.service.EmployeeAssignmentService#updateEmployeeInfo}에서 잡아
	// 409(Conflict)로 응답하도록 고쳤다.
	@Version
	@Column(name = "version", nullable = false)
	private Integer version = 0;

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
