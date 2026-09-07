package com.dsa.flawless.scheduler;

import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.repository.AttendanceRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AttendanceBatchScheduler {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void createDailyAttendanceRecords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

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
}
