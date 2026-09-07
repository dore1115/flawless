package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 품목별 "현재 재고 수량"을 추적하는 엔티티.
 * ERD 설명 그대로: "품목이 등록되어 있어도 첫 입고 전까지는 해당 품목의 재고 행이
 * 존재하지 않을 수 있음(품목 1개당 최대 1행)." → 그래서 item과 1:1 관계이고,
 * 재고 행은 오직 "입고 처리"를 통해서만 최초 생성(REQ-F-M-005).
 */
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor
public class Stock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ERD: item_id BIGINT, FK, UNIQUE → item 1개당 stock 행은 최대 1개(1:1 관계)
    // FetchType.LAZY: Stock을 조회할 때 Item을 즉시 함께 조회하지 않고,
    // 실제로 item.getName() 등을 호출하는 시점에 그때 가서 조회(불필요한 조회 최소화).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, unique = true)
    private Item item;

    // ERD: quantity INT, 0 이상. 입고 시 증가, 출고 시 감소
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /*
     * [REQ-NF-I-005] 비기능요구사항 결함: 재고 출고 모듈의 동시성 결함
     *   - 증상: 같은 품목에 대해 여러 출고 요청이 동시에 들어오면(예: 재고 5개인데 2개짜리 출고
     *     요청이 동시에 3건 들어옴), InventoryService.outbound()의 "재고 조회 → 수량 비교 →
     *     차감(decrease) → 저장" 네 단계 사이에 아무 락도 없어서, 여러 요청이 전부 "재고 충분함"으로
     *     통과한 뒤 각자 차감해버려 실제 재고보다 더 많은 수량이 출고 처리될 수 있음(최종 재고가
     *     음수가 될 수도 있음) - 전형적인 Lost Update/Race Condition.
     *   - 해결 방법(택 1):
     *     1) 낙관적 락(Optimistic Lock): DB에
     *        `ALTER TABLE stock ADD COLUMN version INT NOT NULL DEFAULT 0;` 로 컬럼 추가 후,
     *        아래처럼 @Version 필드를 살리면 JPA가 저장 시점마다 버전을 자동으로 검사·증가시킴.
     *        이러면 동시에 두 트랜잭션이 같은 Stock 행을 수정하려 할 때, 나중에 커밋하는 쪽이
     *        OptimisticLockingFailureException을 받음 → InventoryService.outbound()에서 그
     *        예외를 잡아 "다시 시도해 주세요" 안내를 하거나 @Retryable로 자동 재시도하도록 고쳐야 함.
     *     2) 비관적 락(Pessimistic Lock): StockRepository.findByItemId()에
     *        `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 붙이면, 한 트랜잭션이 특정 재고 행을 조회하는
     *        순간 DB 차원에서 그 행을 잠가 다른 트랜잭션이 끝날 때까지 기다리게 만듦(동시 요청이
     *        많을 때 낙관적 락보다 더 확실히 막지만, 그만큼 대기 시간이 늘어날 수 있음).
     *   - 지금은 두 방법 다 적용하지 않은 의도적 결함 상태라 @Version 필드를 아래처럼 주석 처리해둠.
     *
     * @Column(name = "version", nullable = false)
     * private int version = 0;
     */


    public Stock(Item item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    /**
     * 입고 처리 시 재고 수량을 늘림.
     * 실제 값 변경 로직을 엔티티 안에 두어(도메인 로직 응집),
     * Service에서는 "얼마나 늘릴지"만 결정하고 "어떻게 늘리는지"는 엔티티가 책임함.
     */
    public void increase(int amount) {
        this.quantity += amount;
    }

    /*
     * [출고 기능(REQ-F-M-006) 추가 시 이 메서드를 새로 추가함]
     * 이유: increase()와 대칭되는 "줄이는" 동작이 이 엔티티에 아직 없어서, 출고 처리를
     *      구현하려면 반드시 필요했음(재고 수량 변경 로직을 서비스가 아니라 엔티티가
     *      책임지는 기존 설계 방침을 그대로 따름).
     * 방법: increase()와 똑같은 형태로, 수량을 amount만큼 빼는 코드만 추가함.
     *      "재고보다 많이 출고하면 안 된다"는 검증은 여기서 하지 않고 InventoryService.outbound()에서
     *      미리 확인한 뒤에만 이 메서드를 호출하도록 함(increase()가 "얼마나 늘릴지는 서비스가 결정"
     *      하는 방식과 동일하게, "출고 가능한지 판단"은 서비스 책임으로 남겨둠).
     */
    public void decrease(int amount) {
        this.quantity -= amount;
    }
}
