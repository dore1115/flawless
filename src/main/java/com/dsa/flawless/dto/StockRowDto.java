package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Item;
import lombok.Getter;

/**
 * IV-002(재고 조회)/IV-005(재고 입출고 이력 조회) 화면에서 품목 하나를 표현하는 DTO.
 * 화면에는 품목명/현재 수량만 필요하므로 Item 엔티티를 그대로 넘기지 않고 id/name만 담는다.
 */
@Getter
public class StockRowDto {
    private final Long id;
    private final String name;

    public StockRowDto(Item item) {
        this.id = item.getId();
        this.name = item.getName();
    }
}
