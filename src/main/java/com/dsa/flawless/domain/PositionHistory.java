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
 * 직책(position) 변경 이력을 남기는 "감사 로그" 엔티티 (REQ-NF-H-005 개선).
 * schema.sql(position_history) 설명: append-only — 한 번 생성된 이력은 수정·삭제하지 않는다.
 * {@link StockHistory}와 동일한 설계 원칙: 값을 바꾸는 메서드(setter류)를 전혀 두지 않는다.
 *
 * H-005가 지적한 결함("삭제 이력(누가/언제/무엇을)이 기록되지 않음")에 맞춰, 현재는
 * {@link com.dsa.flawless.service.PositionService#delete(Long)}에서 직책을 비활성화(soft delete)할
 * 때만 DELETE 이력을 남긴다. 변경 전 값(beforeValue)에는 삭제 직전의 직책명을 남기고, 변경 후 값
 * (afterValue)은 schema.sql 컬럼 설명("삭제 시 NULL")에 따라 비워둔다 — 삭제 후 상태는
 * position.is_active=false로 원본 테이블에 이미 반영되므로 이력에 중복으로 남기지 않는다.
 */
@Entity
@Table(name = "position_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ERD: position_id BIGINT, FK (N:1) - 여러 이력이 하나의 직책을 가리킬 수 있음.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    // ERD: action VARCHAR(20) - CREATE/UPDATE/DELETE.
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private HistoryAction action;

    // ERD상 employee_id는 employee 테이블을 참조하는 FK(N:1)이지만, StockHistory.employeeId와
    // 동일한 이유로 @ManyToOne 대신 employee_id 값(Long)만 그대로 저장한다 — 감사 로그는
    // "누가 처리했는지"만 식별하면 되므로 처리자 엔티티 전체를 로딩할 필요가 없다.
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    private PositionHistory(Position position, HistoryAction action, Long employeeId,
                             String beforeValue, String afterValue) {
        this.position = position;
        this.action = action;
        this.employeeId = employeeId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public static PositionHistory ofDelete(Position position, Long employeeId) {
        return new PositionHistory(position, HistoryAction.DELETE, employeeId,
                position.getPositionName(), null);
    }
}
