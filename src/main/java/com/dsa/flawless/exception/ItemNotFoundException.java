package com.dsa.flawless.exception;

/**
 * 요청받은 품목이 존재하지 않거나(등록되지 않았거나) 이미 삭제(비활성화)되어
 * 처리를 할 수 없을 때 던지는 예외.
 *
 * REQ-NF-G-004 개선: BusinessException을 상속해 GlobalExceptionHandler가 처리한다(404 오류 화면).
 * 생성자로 넘기는 message(itemId 등)는 서버 로그에만 남고, 사용자에겐 ErrorCode.ITEM_NOT_FOUND 메시지만 보인다.
 */
public class ItemNotFoundException extends BusinessException {
    public ItemNotFoundException(String message) {
        super(ErrorCode.ITEM_NOT_FOUND, message);
    }
}
