package com.dsa.flawless.domain;

import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(name = "multiUniqueConstraint", columnNames = {"employee_id", "work_date"})
)
public class Attendance extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    //! N+1 의도적으로 배치
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;
    @Column(name = "clock_in")
    private LocalDateTime clockIn;
    @Enumerated(EnumType.STRING)
    @Column(name = "clock_in_status", nullable = false)
    private ClockInStatus clockInStatus;
    @Column(name = "clock_out")
    private LocalDateTime clockOut;
    @Enumerated(EnumType.STRING)
    @Column(name = "clock_out_status")
    private ClockOutStatus clockOutStatus;
    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;
    @Column(name = "early_leave_minutes", nullable = false)
    private Integer earlyLeaveMinutes = 0;
    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @Builder
    public Attendance(LocalDate workDate, Employee employee) {
        this.employee = employee;
        this.workDate = workDate;
        this.dayType =
                workDate.getDayOfWeek().getValue() == 6 || workDate.getDayOfWeek().getValue() == 7 ? DayType.HOLIDAY : DayType.NORMAL;
        this.clockInStatus = ClockInStatus.ABSENT;
    }

    private boolean isClockedIn() {
        return this.clockIn != null;
    }

    // seconds단위는 버리고, 분 단위만 late_minutes에 저장
    public void clockIn(LocalDateTime now) {
        if (isClockedIn()) {
            throw new BusinessException(ErrorCode.ALREADY_CLOCKED_IN);  //! //! REQ-NF-G-004: Exception handler 생성 필요
        }
        LocalTime CLOCK_IN_CRITERIA = LocalTime.of(9,0,0);
        this.clockIn = now;
        this.lateMinutes = Math.max(calcMin(now.toLocalTime(), CLOCK_IN_CRITERIA), 0);
        this.clockInStatus = this.lateMinutes <= 0 ? ClockInStatus.ON_TIME : ClockInStatus.LATE;
        log.info("{}", lateMinutes);
    }

    private int calcMin(LocalTime now, LocalTime criteria) {
        int hour = now.getHour() - criteria.getHour();
        int min = now.getMinute() - criteria.getMinute();
        return hour * 60 + min;
    }
}