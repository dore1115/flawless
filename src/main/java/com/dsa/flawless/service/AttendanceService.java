package com.dsa.flawless.service;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.dto.AttendanceDto;
import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.exception.ErrorCode;
import com.dsa.flawless.repository.AttendanceRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;


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
            throw new RuntimeException("출근 가능 시각이 지났습니다."); //! REQ-NF-G-004: Exception handler 생성 필요
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
                username, now.toLocalDate()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN)); //! REQ-NF-G-004: Exception handler 필요
        attendance.clockOut(now);
    }
    // 조회 처리
    public List<AttendanceDto> getList(String employeeUsername, Integer year, Integer month) {
        LocalDate now = LocalDate.now(clock);
        int targetYear = year == null ? now.getYear() : year;
        int targetMonth = month == null ? now.getMonthValue() : month;

        YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);
        List<Attendance> attendances = attendanceRepository.findAllByEmployeeUsernameAndWorkDateBetween(
                employeeUsername, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        return attendances.stream()
                .map(attendance -> new AttendanceDto(attendance.getWorkDate(), attendance.getClockIn(), attendance.getClockOut()))
                .collect(Collectors.toList());
    }


}

