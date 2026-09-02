package com.dsa.flawless.dto;

import com.dsa.flawless.domain.StockHistory;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 입고 폼 화면에 "최근 입고 이력 5건"을 참고용으로 보여주기 위한 DTO.
 * (화면설계서 IV-003의 Model 전달 데이터 표: recentInbound)
 */
@Getter
public class InboundHistoryDto {
    private final Long itemId;
    private final String itemName;
    private final int quantity;
    private final LocalDateTime processedAt;

    public InboundHistoryDto(StockHistory history) {
        this.itemId = history.getItem().getId();
        this.itemName = history.getItem().getName(); // LAZY 연관관계이지만, 트랜잭션 안에서 호출되므로 조회 가능
        this.quantity = history.getQuantity();
        this.processedAt = history.getCreatedAt();
    }
}
