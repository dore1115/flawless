package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByActiveTrue();
}
