package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
	Optional<Employee> findByUsername(String username);
	boolean existsByUsername(String username);
	boolean existsByEmail(String email);
}
