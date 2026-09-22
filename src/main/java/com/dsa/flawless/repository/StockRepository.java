package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Stock;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Stock(재고) 엔티티에 대한 데이터 접근 계층.
 */
public interface StockRepository extends JpaRepository<Stock, Long> {

    // 품목 id로 재고 행을 찾음. 아직 한 번도 입고된 적 없는 품목이면 결과가 없을 수 있음
    // (ERD 설명: "첫 입고 전까지는 해당 품목의 재고 행이 존재하지 않을 수 있음")
    Optional<Stock> findByItemId(Long itemId);

    //! [REQ-NF-I-003] 해결(2026-09-21): 재고 조회 화면(InventoryService.getCurrentQuantities())이
    //! 품목 하나하나마다 findByItemId()를 개별 호출하던 N+1 쿼리를 없애기 위해 새로 추가한
    //! 배치 조회 메서드. "item.id IN (:itemIds)" 한 번의 쿼리로 여러 품목의 재고 행을 한꺼번에 가져옴.
    List<Stock> findByItemIdIn(Collection<Long> itemIds);

    //! [REQ-NF-I-005] 해결: InventoryService.outbound()의 "조회 → 수량 비교 → 차감 → 저장" 네 단계
    //! 사이에 락이 없어서 동시 출고 요청 시 Lost Update가 발생하던 결함(Stock.java의 주석,
    //! InventoryService.outbound() 주석 참고)을 고치기 위해 추가한 메서드. 조회 조건은 findByItemId()와
    //! 동일하지만 @Lock(PESSIMISTIC_WRITE)를 붙여 해당 재고 행에 DB 수준 배타적 잠금(SELECT ... FOR UPDATE)을
    //! 걸어, 트랜잭션이 끝날 때까지 다른 트랜잭션이 같은 행을 동시에 읽고 갱신하지 못하게 막음.
    //! 락이 필요한 outbound()에서만 이 메서드를 쓰고, 단순 조회 용도인 inbound()/
    //! getActiveItemOptionsWithStock() 등은 불필요한 락으로 인한 성능 저하를 피하기 위해 기존
    //! findByItemId()를 그대로 씀.
    //! findByItemIdForUpdate라는 이름을 findBy... 파생 쿼리 규칙으로 그대로 두면 Spring Data JPA가
    //! "ForUpdate"를 Stock 엔티티의 프로퍼티/키워드로 해석하려다 실패해서 애플리케이션이 기동조차
    //! 되지 않음(QueryCreationException). 그래서 조회 조건은 findByItemId()와 동일하게 JPQL로
    //! 명시하고, @Lock만 얹어서 해결함.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.item.id = :itemId")
    Optional<Stock> findByItemIdForUpdate(@Param("itemId") Long itemId);
}
