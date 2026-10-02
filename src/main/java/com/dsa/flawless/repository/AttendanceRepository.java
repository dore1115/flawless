package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeUsernameAndWorkDate(String username, LocalDate date);


    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate today);

    List<Attendance> findAllByEmployeeUsername(String username);


    List<Attendance> findAllByClockInIsNotNullAndClockOutIsNull();
}
