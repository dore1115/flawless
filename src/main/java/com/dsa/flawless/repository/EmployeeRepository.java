package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
	// TODO(REQ-NF-M-003 하드 삭제 + 감사로그 부재): JpaRepository를 상속하고 있어
	// delete(Employee)/deleteById(Long)를 그대로 쓰면 즉시 하드 삭제된다.
	// 사원 삭제 기능 구현 시 이 메서드들을 직접 호출하지 말고, Employee에 소프트 삭제
	// 필드(status 등)를 추가한 뒤 상태만 바꾸는 update 쿼리/메서드를 새로 만들 것.
	Optional<Employee> findByUsername(String username);
	boolean existsByUsername(String username);
	boolean existsByEmail(String email);

	// REQ-F-S-006/008(직책·부서 삭제): 삭제 차단 여부 판단 및 "소속 사원 목록" 표시에 사용한다.
	List<Employee> findByPosition(Position position);
	List<Employee> findByDepartment(Department department);

    Employee getReferenceByUsername(String username);

    // REQ-NF-H-007 개선: HR-001(사원 목록) 화면이 기존에는 findAll()(= "select e from Employee e")로
    // 조회했는데, department/position이 @ManyToOne(fetch = EAGER)로 선언돼 있어도 JPQL에 명시적인
    // JOIN FETCH가 없으면 Hibernate가 각 연관관계를 행마다 추가 SELECT로 채운다 — 즉 EAGER 설정만으로는
    // N+1을 막지 못한다(실측으로 확인됨: 사원 목록 1쿼리 + 부서/직책 개별 쿼리가 각각 추가로 발생).
    // department_id/position_id는 DB(employee 테이블)에서 NOT NULL이라 모든 사원이 항상 값을 갖고
    // 있으므로, LEFT JOIN이 아닌 INNER JOIN FETCH를 써도 조회 결과에서 사원이 누락될 위험이 없다.
    @Query("SELECT e FROM Employee e JOIN FETCH e.department JOIN FETCH e.position")
    List<Employee> findAllWithDepartmentAndPosition();
}
