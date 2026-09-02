package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
	Optional<Employee> findByUsername(String username);
	boolean existsByUsername(String username);
	boolean existsByEmail(String email);

	// REQ-F-S-006/008(직책·부서 삭제): 삭제 차단 여부 판단 및 "소속 사원 목록" 표시에 사용한다.
	List<Employee> findByPosition(Position position);
	List<Employee> findByDepartment(Department department);

}
