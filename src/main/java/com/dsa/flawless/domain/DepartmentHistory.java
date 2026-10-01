package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서(department) 변경 이력을 남기는 "감사 로그" 엔티티 (REQ-NF-H-005 개선).
 * {@link PositionHistory}와 완전히 동일한 설계 — schema.sql(department_history) 설명도
 * append-only이며, 값을 바꾸는 메서드(setter류)를 두지 않는다.
 *
 * 현재는 {@link com.dsa.flawless.service.DepartmentService#delete(Long)}에서 부서를
 * 비활성화(soft delete)할 때만 DELETE 이력을 남긴다. 자세한 설계 이유는 {@link PositionHistory}
 * 주석 참고.
 */
@Entity
@Table(name = "department_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ERD: department_id BIGINT, FK (N:1) - 여러 이력이 하나의 부서를 가리킬 수 있음.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // ERD: action VARCHAR(20) - CREATE/UPDATE/DELETE.
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private HistoryAction action;

    // PositionHistory.employeeId와 동일한 이유로 @ManyToOne 대신 값(Long)만 그대로 저장한다.
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    private DepartmentHistory(Department department, HistoryAction action, Long employeeId,
                               String beforeValue, String afterValue) {
        this.department = department;
        this.action = action;
        this.employeeId = employeeId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public static DepartmentHistory ofDelete(Department department, Long employeeId) {
        return new DepartmentHistory(department, HistoryAction.DELETE, employeeId,
                department.getDepartmentName(), null);
    }
}
