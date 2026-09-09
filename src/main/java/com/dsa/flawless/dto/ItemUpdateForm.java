package com.dsa.flawless.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IV-001 화면(품목 관리)의 "품목 정보 수정" 모달 폼 바인딩 객체.
 * REQ-F-S-010. 화면 시안상 현재 재고 수량/등록일시/수정일시는 읽기 전용 표시만 하고
 * 이 폼으로 제출되지 않아서(수정 가능한 값은 품목명뿐) name 필드만 가짐.
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemUpdateForm {

    @NotBlank(message = "품목명을 입력해 주세요.")
    // ERD/DB 컬럼이 VARCHAR(100)이라, 초과 입력 시 DB 예외(500)로 튕기던 걸 막기 위해 추가함.
    @Size(max = 100, message = "품목명은 100자를 초과할 수 없습니다.")
    private String name;
}
