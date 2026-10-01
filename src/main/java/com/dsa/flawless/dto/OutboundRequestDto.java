package com.dsa.flawless.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 재고 출고 폼(inventory/outbound.html)에서 넘어오는 입력값을 받는 DTO.
 * 입고 화면의 InboundRequestDto와 필드 구성은 동일함(품목 id, 수량).
 *
 * Entity를 화면과 직접 주고받지 않고 DTO로 분리해두는 이유는 InboundRequestDto와 동일함
 * (화면설계서 "공통 UI 규칙" - Entity를 화면/응답에 그대로 노출하지 않음).
 *
 * 화면설계서 IV-004 "입력 항목" 기준 필드:
 *   itemId(품목, 필수) / quantity(출고 수량, 필수, 1 이상 정수)
 */
@Getter
@Setter
@ToString // AuditLogAspect가 감사 로그에 요청 내용(itemId, quantity)을 남길 때 사용
@NoArgsConstructor
public class OutboundRequestDto {

    // REQ-F-M-006 / 화면설계서: "품목을 선택해 주세요." 오류 메시지와 매칭
    @NotNull(message = "품목을 선택해 주세요.")
    private Long itemId;

    // REQ-F-M-006: "수량은 1개 이상만 입력 가능"
    // @Min은 값이 null이 아닐 때만 검사하므로 @NotNull을 함께 붙여 null도 막음.
    @NotNull(message = "1 이상의 수량을 입력해 주세요.")
    @Min(value = 1, message = "1 이상의 수량을 입력해 주세요.")
    private Integer quantity;
}
