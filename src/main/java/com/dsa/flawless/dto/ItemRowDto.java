package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Item;
import lombok.Getter;

/**
 * IV-001 화면(품목 관리) 목록의 한 행에 표시할 정보만 담는 DTO.
 * Item 엔티티 전체(생성일시 등)를 화면에 그대로 넘기지 않고 필요한 값만 골라서 전달함
 * (화면설계서 공통 UI 규칙: "민감정보: Entity를 화면/응답에 그대로 넘기지 않고 DTO로 변환").
 *
 * status(상태) 배지는 item 테이블에 별도 컬럼이 없고(ERD 04_ERD_테이블정의서 기준) is_deleted만
 * 있으므로, "활성/비활성"은 deleted 값에서 화면(Thymeleaf)이 파생시켜 표시.
 */
@Getter
public class ItemRowDto {
    private final Long id;
    private final String name;
    private final boolean deleted;

    public ItemRowDto(Item item) {
        this.id = item.getId();
        this.name = item.getName();
        this.deleted = item.isDeleted();
    }
}
