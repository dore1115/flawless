package com.dsa.flawless.service;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.exception.ErrorCode;
import com.dsa.flawless.repository.AttendanceRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.rmi.NoSuchObjectException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    // 출근 처리
    @Transactional
    public void clockIn(Long employeeId) {
        LocalTime CLOCK_IN_LIMIT = LocalTime.of(14,0,0);
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isAfter(CLOCK_IN_LIMIT)) {
            throw new RuntimeException("출근 가능 시각이 지났습니다."); //! REQ-NF-G-004: Exception handler 생성 필요
        }
        Attendance attendance = attendanceRepository.findByEmpIdAndWorkDate(employeeId, now.toLocalDate()).orElseGet(()->attendanceRepository.save(
                Attendance.builder()
                        .employee(employeeRepository.getReferenceById(employeeId))
                        .workDate(now.toLocalDate())
                        .build()));
        attendance.clockIn(now);
    }
    //TODO 퇴근 처리
    //TODO 조회 처리
}
