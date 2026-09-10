package com.dsa.flawless.dto;

import com.dsa.flawless.domain.MoveType;
import com.dsa.flawless.domain.StockHistory;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 재고 입출고 이력 조회(IV-005, REQ-F-S-012) 모달에 보여줄 이력 한 줄.
 * 요구사항 원문: "각 내역에는 품목, 처리 구분(입고/출고), 처리 수량, 처리 일시, 처리자 정보가
 * 함께 표시된다." - 품목은 이미 모달을 연 품목 하나로 고정돼 있어서 이 DTO엔 따로 담지 않음.
 */
@Getter
public class StockHistoryRowDto {
    private final MoveType moveType;
    private final int quantity;
    private final LocalDateTime processedAt;
    private final String employeeName;

    public StockHistoryRowDto(StockHistory history, String employeeName) {
        this.moveType = history.getMoveType();
        this.quantity = history.getQuantity();
        this.processedAt = history.getCreatedAt();
        this.employeeName = employeeName;
    }
}
