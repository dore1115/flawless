package com.dsa.flawless.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IV-001 화면(품목 관리)의 "새 품목 등록" 모달 폼 바인딩 객체.
 *
 * REQ-F-S-009: "관리자는 품목명을 입력하여 새로운 품목을 시스템에 등록할 수 있다."
 * 필수값 검증(@NotBlank)만 여기서 하고, "이미 등록된 품목명인지"는 DB 조회가 필요한 검사
 * ItemService.create()에서 확인한다(DuplicateItemNameException 참고).
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemCreateForm {

    @NotBlank(message = "품목명을 입력해 주세요.")
    private String name;
}
