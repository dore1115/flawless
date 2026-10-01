package com.dsa.flawless.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 재고 입고 폼(inventory/inbound.html)에서 넘어오는 입력값을 받는 DTO.
 *
 * Entity를 화면/폼과 직접 주고받지 않고 DTO를 따로 두는 이유:
 * - 화면설계서 "공통 UI 규칙" 표에 "민감정보: Entity를 화면/응답에 그대로 넘기지 않고
 *   DTO로 변환해 노출 방지"라는 규칙이 있음. 지금 당장은 민감정보가 없지만,
 *   Entity가 화면과 직접 엮이면 나중에 Entity 구조를 바꿀 때 화면 코드까지 영향을 받으므로
 *   처음부터 분리해 둠.
 *
 * 화면설계서 IV-003 "입력 항목" 표 기준 필드:
 *   itemId(품목, 필수) / quantity(입고 수량, 필수, 1 이상 정수)
 */
@Getter
@Setter
@ToString // AuditLogAspect가 감사 로그에 요청 내용(itemId, quantity)을 남길 때 사용
@NoArgsConstructor
public class InboundRequestDto {

    // REQ-F-M-005 / 화면설계서: "품목을 선택해 주세요." 오류 메시지와 매칭
    @NotNull(message = "품목을 선택해 주세요.")
    private Long itemId;

    // 화면설계서: "1 이상의 수량을 입력해 주세요." 오류 메시지와 매칭
    // @Min은 값이 null이 아닐 때만 검사하므로 @NotNull을 함께 붙여 null도 막음.
    @NotNull(message = "1 이상의 수량을 입력해 주세요.")
    @Min(value = 1, message = "1 이상의 수량을 입력해 주세요.")
    private Integer quantity;
}
