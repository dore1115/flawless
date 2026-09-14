package com.dsa.flawless.scheduler;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.repository.AttendanceRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AttendanceBatchScheduler {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final Clock clock;
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void createDailyAttendanceRecords() {
        LocalDate today = LocalDate.now(clock);

        List<Employee> employee = employeeRepository.findAll();
        List<Attendance> newRecords = employee.stream()
                .filter((emp) -> !attendanceRepository.existsByEmployeeIdAndWorkDate(emp.getId(), today))
                .map(emp-> Attendance.builder()
                        .employee(emp)
                        .workDate(today)
                        .build()
                ).toList();

        attendanceRepository.saveAll(newRecords);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void clockOutBatchProcess() {
        List<Attendance> attendances = attendanceRepository.findAllByClockInIsNotNullAndClockOutIsNull();

        for (Attendance attendance : attendances) {
            LocalDateTime clockIn = attendance.getClockIn();
            attendance.clockOut(LocalDateTime.of(clockIn.getYear(), clockIn.getMonth(), clockIn.getDayOfMonth(), 18, 0, 0));
        }

    }
}