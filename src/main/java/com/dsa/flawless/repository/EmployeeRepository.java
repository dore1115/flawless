package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

//! REQ-NF-H-007 N+1 (실측 먼저 필요)
//! EmployeeRepository에 join fetch 쿼리를 추가해서 EmployeeAssignmentService.findAll()이 그걸 쓰게 바꾸기
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
}
