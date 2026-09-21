package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Item;
import lombok.Getter;

/**
 * 재고 조회(IV-002, REQ-F-S-011) 목록의 한 행.
 *
 * //! [REQ-NF-I-001] 해결: 재고 조회 화면이 Item 엔티티를 변환 없이 그대로 Model에 담아 넘기던
 * //! 결함을 해결하기 위해 새로 만든 응답 DTO. ItemRowDto(품목 관리 화면용)와 같은 패턴이되,
 * //! 재고 조회 화면은 요구사항 원문(REQ-F-S-011) 그대로 "품목명, 현재 수량"만 보여주면 되고
 * //! 수정 기능도 없어서 deleted/createdAt/updatedAt 같은 내부 필드는 아예 담지 않음.
 * //! id는 화면에 직접 노출되진 않지만, 품목명 클릭 시 이동하는 재고 입출고 이력 조회(IV-005)
 * //! 링크(GET /inventory/{id}/history)를 만드는 데 필요해서 포함함.
 */
@Getter
public class StockRowDto {
    private final Long id;
    private final String name;
    private final int quantity;

    public StockRowDto(Item item, int quantity) {
        this.id = item.getId();
        this.name = item.getName();
        this.quantity = quantity;
    }
}
