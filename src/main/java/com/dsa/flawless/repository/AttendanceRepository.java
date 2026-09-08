package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Predicate;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    @Query("select a from Attendance a where a.employee.id=:employeeId and a.workDate=:date")
    Optional<Attendance> findByEmpIdAndWorkDate(Long employeeId, LocalDate date);


    boolean existsByEmployeeIdAndWorkDate(Long empId, LocalDate today);
}
