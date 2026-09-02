package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Item;
import lombok.Getter;

/**
 * 출고 폼의 "품목 선택" 드롭다운에 표시할 정보를 담는 DTO.
 *
 * 입고 화면에서 쓰는 ItemOptionDto(id, name만 있음)와 달리, 출고 화면 디자인 시안(IV-004)에는
 * 품목을 선택했을 때 "현재 재고: N개"를 옆에 같이 보여주는 부분이 있어서 quantity 필드를 추가로 둠.
 * 재고 기록이 아직 없는 품목은 0으로 취급함(REQ-F-M-006: 출고는 재고 기록을 자동 생성하지 않음).
 */
@Getter
public class OutboundItemOptionDto {
    private final Long id;
    private final String name;
    private final int quantity;

    public OutboundItemOptionDto(Item item, int quantity) {
        this.id = item.getId();
        this.name = item.getName();
        this.quantity = quantity;
    }
}
