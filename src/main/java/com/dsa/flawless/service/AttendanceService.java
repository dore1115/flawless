package com.dsa.flawless.service;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.exception.ErrorCode;
import com.dsa.flawless.repository.AttendanceRepository;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.rmi.NoSuchObjectException;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;

    //TODO 출근 처리
    public void clockIn(Long employeeId) {
        Attendance attendance = attendanceRepository.findById(employeeId).orElseThrow();
        attendance.clockIn();
    }
    //TODO 퇴근 처리
    //TODO 조회 처리
}
