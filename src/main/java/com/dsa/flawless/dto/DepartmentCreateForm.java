package com.dsa.flawless.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HR-004 화면(부서 목록)의 "부서 신설" 폼 바인딩 객체.
 *
 * REQ-F-S-007: "회사 차원에서 새로운 부서를 추가 등록한다."
 *
 * REQ-F-S-005(직책 신설)와 마찬가지로 요구사항정의서 비고란에는 "부서명/부서코드 중복 검증,
 * 필수값(부서코드, 부서명) 누락 검증"이라고 적혀 있었으나, 부서코드는 담당자가 요구사항정의서에서
 * 지우는 걸 깜빡한 잔재 항목으로 확인됨(REQ-F-S-005_직책신설_구현노트 참고) — 테이블정의서
 * (department 테이블)에는 부서코드 컬럼이 없고 department_name 하나뿐이며, 이 폼도 그에 맞춰
 * departmentName 하나만 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class DepartmentCreateForm {

    private String departmentName;
}
