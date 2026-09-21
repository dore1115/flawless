package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
