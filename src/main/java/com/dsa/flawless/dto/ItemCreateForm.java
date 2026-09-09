package com.dsa.flawless.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    // ERD/DB 컬럼이 VARCHAR(100)이라, 초과 입력 시 DB 예외(500)로 튕기던 걸 막기 위해 추가함.
    @Size(max = 100, message = "품목명은 100자를 초과할 수 없습니다.")
    private String name;
}
