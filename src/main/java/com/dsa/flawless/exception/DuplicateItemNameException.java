package com.dsa.flawless.exception;

/**
 * REQ-F-S-009(품목 등록)·REQ-F-S-010(품목 정보 수정) 비고란: "동일한 품목명으로 중복 등록/변경은
 * 허용하지 않음"을 위반했을 때(이미 존재하는 품목명으로 등록/수정을 시도했을 때) 던지는 예외.
 *
 * REQ-NF-G-004 개선: BusinessException을 상속해 GlobalExceptionHandler가 처리한다(409 오류 화면).
 * 생성자로 넘기는 message는 서버 로그에만 남고, 사용자에겐 ErrorCode.DUPLICATE_ITEM_NAME 메시지만 보인다.
 */
public class DuplicateItemNameException extends BusinessException {
    public DuplicateItemNameException(String message) {
        super(ErrorCode.DUPLICATE_ITEM_NAME, message);
    }
}
