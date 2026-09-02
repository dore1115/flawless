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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입고/출고 처리로 재고 수량이 변경된 내역을 남기는 "이력" 엔티티.
 * ERD 설명: "조회만 가능하며 수정·삭제하지 않음."
 * → 그래서 이 엔티티에는 값을 바꾸는 메서드(setter류)를 전혀 두지 않음.
 *   한 번 생성된 이력은 절대 수정되지 않는다는 규칙을 코드로도 강제한 것.
 */
@Entity
@Table(name = "stock_history")
@Getter
@NoArgsConstructor
public class StockHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ERD: item_id BIGINT, FK (N:1) - 여러 이력이 하나의 품목을 가리킬 수 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // ERD: move_type VARCHAR(20) - IN(입고)/OUT(출고)
    @Enumerated(EnumType.STRING) // DB에는 0,1 같은 숫자가 아니라 "IN","OUT" 문자열로 저장(가독성/안전성)
    @Column(name = "move_type", nullable = false, length = 20)
    private MoveType moveType;

    // ERD: quantity INT, 1 이상 - 처리 수량
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /*
     * ERD상으로는 employee_id가 employee 테이블을 참조하는 FK(N:1)임.
     * 다만 employee(사원) 엔티티는 다른 팀원이 "회원가입/로그인" 기능에서
     * 별도로 작업하고 있어, 현재 이 저장소에는 아직 Employee 엔티티가 없음.
     *
     * 그래서 지금은 @ManyToOne 연관관계 대신, employee_id 값(Long)만 그대로 저장.
     * → DB 테이블 구조(FK 포함)는 ERD 그대로 만들어 두고, 자바 쪽 연관 매핑만
     *   Employee 엔티티가 합쳐진 뒤에 @ManyToOne으로 교체하면 됨.
     * → 그 전까지는 "누가 처리했는지" 식별할 값이 필요하므로, 로그인 기능이 붙기 전까지는
     *   InventoryController에서 임시 값을 넣어 동작을 확인(코드 내 TODO 참고).
     */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    public StockHistory(Item item, MoveType moveType, int quantity, Long employeeId) {
        this.item = item;
        this.moveType = moveType;
        this.quantity = quantity;
        this.employeeId = employeeId;
    }
}
