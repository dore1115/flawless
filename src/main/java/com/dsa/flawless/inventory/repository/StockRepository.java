package com.dsa.flawless.inventory.repository;

import com.dsa.flawless.inventory.entity.Stock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stock(재고) 엔티티에 대한 데이터 접근 계층.
 */
public interface StockRepository extends JpaRepository<Stock, Long> {

    // 품목 id로 재고 행을 찾음. 아직 한 번도 입고된 적 없는 품목이면 결과가 없을 수 있음
    // (ERD 설명: "첫 입고 전까지는 해당 품목의 재고 행이 존재하지 않을 수 있음")
    Optional<Stock> findByItemId(Long itemId);
}
