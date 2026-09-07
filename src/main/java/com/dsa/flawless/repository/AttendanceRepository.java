package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.domain.Employee;
import com.sun.jdi.connect.AttachingConnector;
import org.hibernate.internal.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.function.Predicate;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeIdAndWorkDate(Long EmployeeId, LocalDate date);


    boolean existsByEmployeeIdAndWorkDate(Long empId, LocalDate today);
}
