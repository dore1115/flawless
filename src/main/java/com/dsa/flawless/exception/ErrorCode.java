package com.dsa.flawless.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    ALREADY_CLOCKED_IN(HttpStatus.CONFLICT, "이미 출근 처리된 사용자입니다."),
    NOT_CLOCKED_IN(HttpStatus.BAD_REQUEST, "출근 기록이 없어 퇴근 처리를 할 수 없습니다."),
    CLOCK_IN_TIME_PASSED(HttpStatus.BAD_REQUEST, "출근 가능 시각이 지났습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "대상 품목이 없거나 이미 삭제된 품목입니다."),
    DUPLICATE_ITEM_NAME(HttpStatus.CONFLICT, "이미 등록된 품목명입니다.");

    // 이후 재고/인사 모듈 예외도 여기에 계속 추가

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
