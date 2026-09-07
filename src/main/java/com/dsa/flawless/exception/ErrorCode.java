package com.dsa.flawless.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    ALREADY_CLOCKED_IN(HttpStatus.CONFLICT, "이미 출근 처리된 사용자입니다."),
    NOT_CLOCKED_IN(HttpStatus.BAD_REQUEST, "출근 기록이 없어 퇴근 처리를 할 수 없습니다.");

    // 이후 재고/인사 모듈 예외도 여기에 계속 추가

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
