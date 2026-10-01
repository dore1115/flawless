package com.dsa.flawless.exception;

/**
 * 출고하려는 수량이 현재 재고 수량보다 많을 때(재고 부족) 던지는 예외.
 * REQ-F-M-006: "요청 수량이 더 많으면 출고를 처리하지 않고 요청을 거부하며,
 *              충분하면 입력한 수량만큼 차감하여 저장한다."
 *
 * 출고 폼 화면을 유지한 채 quantity 필드 오류("현재 재고: N개")로 보여줘야 하므로
 * GlobalExceptionHandler가 아닌 InventoryController가 직접 try-catch로 잡아 처리함.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
