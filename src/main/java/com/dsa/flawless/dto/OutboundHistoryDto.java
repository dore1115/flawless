package com.dsa.flawless.dto;

import com.dsa.flawless.domain.StockHistory;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 출고 폼 화면에 "최근 출고 이력 5건"을 참고용으로 보여주기 위한 DTO.
 * 입고 화면의 InboundHistoryDto와 필드 구성은 동일하지만, "입고 이력 전용 DTO"라는
 * 이름/문서 혼동을 피하기 위해 출고 전용으로 별도 파일을 둠.
 */
@Getter
public class OutboundHistoryDto {
    private final Long itemId;
    private final String itemName;
    private final int quantity;
    private final LocalDateTime processedAt;

    public OutboundHistoryDto(StockHistory history) {
        this.itemId = history.getItem().getId();
        this.itemName = history.getItem().getName(); // LAZY 연관관계이지만, 트랜잭션 안에서 호출되므로 조회 가능
        this.quantity = history.getQuantity();
        this.processedAt = history.getCreatedAt();
    }
}
