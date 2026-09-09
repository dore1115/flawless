package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Item;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * IV-001 화면(품목 관리) 목록의 한 행 + 그 행의 "품목 정보 수정" 모달에 필요한 정보를 함께 담는 DTO.
 * 목록과 수정 모달이 같은 화면(list.html) 안에 있어서, "수정" 버튼을 눌렀을 때 서버에 다시
 * 요청하지 않고 Thymeleaf가 화면을 그릴 때 행마다 모달까지 함께 그려두는 방식을 씀.
 *
 * status(상태) 배지는 item 테이블에 별도 컬럼이 없고 is_deleted만 있어서(ERD 기준),
 * "활성/비활성"은 deleted 값에서 화면이 파생시켜 표시.
 */
@Getter
public class ItemRowDto {
    private final Long id;
    private final String name;
    private final boolean deleted;
    private final int currentStockQuantity;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ItemRowDto(Item item, int currentStockQuantity) {
        this.id = item.getId();
        this.name = item.getName();
        this.deleted = item.isDeleted();
        this.currentStockQuantity = currentStockQuantity;
        this.createdAt = item.getCreatedAt();
        this.updatedAt = item.getUpdatedAt();
    }
}
