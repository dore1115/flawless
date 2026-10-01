package com.dsa.flawless.service;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.exception.ErrorCode;
import com.dsa.flawless.repository.AttendanceRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.accessibility.AccessibleText;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public String getClockIn(String username) {
        Attendance attendance = attendanceRepository.findByEmployeeUsernameAndWorkDate(username, LocalDate.now(clock)).orElseGet(()->null);
        if (attendance == null) {
            return "";
        }
        return attendance.getClockIn().getHour() + ":" + attendance.getClockIn().getMinute();

    }
    // 출근 처리
    @Transactional
    public void clockIn(String username) {
        LocalTime CLOCK_IN_LIMIT = LocalTime.of(14,0,0);
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.toLocalTime().isAfter(CLOCK_IN_LIMIT)) {
            throw new BusinessException(ErrorCode.CLOCK_IN_TIME_PASSED); //! REQ-NF-G-004 개선: GlobalExceptionHandler에서 처리
        }
        Attendance attendance = attendanceRepository.findByEmployeeUsernameAndWorkDate(username, now.toLocalDate()).orElseGet(()->attendanceRepository.save(
                Attendance.builder()
                        .employee(employeeRepository.getReferenceByUsername(username))
                        .workDate(now.toLocalDate())
                        .build()));
        attendance.clockIn(now);
    }

    // 퇴근 처리
    @Transactional
    public void clockOut(String username) {
        LocalDateTime now = LocalDateTime.now(clock);

        Attendance attendance = attendanceRepository.findByEmployeeUsernameAndWorkDate(
                username, now.toLocalDate()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN)); //! REQ-NF-G-004 개선: GlobalExceptionHandler에서 처리
        attendance.clockOut(now);
    }
    // 조회 처리
    //! 전체 조회 후 원하는 조건에 맞는 데이터를 조회하기 때문에 성능 저하를 유발할 가능성이 있다.
    public List<Attendance> getList(String employeeUsername, Integer year, Integer month) {
        List<Attendance> attendances = attendanceRepository.findAllByEmployeeUsername(employeeUsername);
        List<Attendance> monthlyAttendance = new ArrayList<>();

        LocalDate now = LocalDate.now(clock);
        int targetYear = year == null ? now.getYear() : year;
        int targetMonth = month == null ? now.getMonthValue() : month;
        for (Attendance attendance : attendances) {
            if (attendance.getWorkDate().getYear() == targetYear && attendance.getWorkDate().getMonthValue() == targetMonth ) {
                monthlyAttendance.add(attendance);
            }
        }

        return monthlyAttendance;
    }


}

