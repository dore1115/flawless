package com.dsa.flawless.exception;

/**
 * REQ-F-C-008(품목 삭제) 비고: "현재 재고 수량이 0이 아니면 삭제할 수 없으며 오류 메시지가
 * 표시됨"을 위반했을 때(재고가 남아있는 품목을 삭제하려 할 때) 던지는 예외.
 * DepartmentInUseException/PositionInUseException과 같은 명명 규칙을 따름.
 */
public class ItemInUseException extends RuntimeException {
    public ItemInUseException(String message) {
        super(message);
    }
}
