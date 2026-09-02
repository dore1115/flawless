package com.dsa.flawless.exception;

/**
 * 출고하려는 수량이 현재 재고 수량보다 많을 때(재고 부족) 던지는 예외.
 * REQ-F-M-006: "요청 수량이 더 많으면 출고를 처리하지 않고 요청을 거부하며,
 *              충분하면 입력한 수량만큼 차감하여 저장한다."
 *
 * ItemNotFoundException과 마찬가지로 이 프로젝트에는 아직 GlobalExceptionHandler가
 * 없어서(REQ-NF-G-004, 의도적으로 비워둔 결함) InventoryController가 이 예외를
 * 직접 try-catch로 잡아 처리함.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
