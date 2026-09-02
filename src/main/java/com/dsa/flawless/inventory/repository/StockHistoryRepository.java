package com.dsa.flawless.inventory.repository;

import com.dsa.flawless.inventory.entity.MoveType;
import com.dsa.flawless.inventory.entity.StockHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * StockHistory(입출고 이력) 엔티티에 대한 데이터 접근 계층.
 */
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    // 입고 폼 화면(IV-003)에 참고용으로 보여줄 "최근 입고 이력 5건"을 위한 조회 메서드.
    // moveType으로 IN/OUT을 구분하고, 최신순(createdAt 내림차순)으로 정렬.
    // 개수 제한은 Service에서 PageRequest.of(0, 5)로 넘겨 처리.
    List<StockHistory> findByMoveTypeOrderByCreatedAtDesc(MoveType moveType, Pageable pageable);
}
