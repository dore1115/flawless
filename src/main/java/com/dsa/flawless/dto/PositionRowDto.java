package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Position;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * HR-003(직책 목록) 화면의 한 행을 표현하는 DTO.
 */
@Getter
public class PositionRowDto {
    private final Long id;
    private final String positionName;
    private final boolean active;
    private final LocalDateTime createdAt;

    public PositionRowDto(Position position) {
        this.id = position.getId();
        this.positionName = position.getPositionName();
        this.active = position.isActive();
        this.createdAt = position.getCreatedAt();
    }
}
