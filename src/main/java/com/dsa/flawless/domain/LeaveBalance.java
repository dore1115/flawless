package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "leave_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "year"})
)
public class LeaveBalance extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    //! IDX 설정 필요
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee employee;
    @Column(name = "year", nullable = false)
    private Short year;
    @Column(name = "annual_leave_total", nullable = false)
    private Integer annualLeaveTotal = 20;
    @Column(name = "annual_leave_used", nullable = false)
    private Integer annualLeaveUsed = 0;
    @Column(name = "sick_leave_total", nullable = false)
    private Integer sickLeaveTotal = 10;
    @Column(name = "sick_leave_used", nullable = false)
    private Integer sickLeaveUsed = 0;
    @Column(name = "official_leave_used", nullable = false)
    private Integer officialLeaveUsed = 0;

}
