package com.dsa.flawless.inventory.dto;

import com.dsa.flawless.inventory.entity.Item;
import lombok.Getter;

/**
 * 입고 폼의 "품목 선택" 드롭다운(select 태그)에 표시할 최소 정보만 담는 DTO.
 * Item 엔티티 전체(생성일시 등)를 화면에 넘기지 않고 id/name만 골라서 전달.
 */
@Getter
public class ItemOptionDto {
    private final Long id;
    private final String name;

    public ItemOptionDto(Item item) {
        this.id = item.getId();
        this.name = item.getName();
    }
}
