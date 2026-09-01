package com.dsa.flawless.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HR-003 화면(직책 목록)의 "직책 신설" 폼 바인딩 객체.
 *
 * REQ-F-S-005: "회사 차원에서 새로운 직책 종류를 추가 등록한다."
 *
 * 요구사항정의서 비고란에는 "직책명/직책코드 중복 검증, 필수값(직책코드, 직책명) 누락 검증"이라고
 * 적혀 있지만, 테이블정의서(position 테이블)에는 직책코드 컬럼 자체가 없다 — position_name 하나뿐이다.
 * 그래서 이 폼도 positionName 하나만 받는다. 이 불일치는 팀 확인이 필요한 항목으로 별도 기록해 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PositionCreateForm {

    private String positionName;
}
