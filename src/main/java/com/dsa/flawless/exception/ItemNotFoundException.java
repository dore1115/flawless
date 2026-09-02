package com.dsa.flawless.exception;

/**
 * 요청받은 품목이 존재하지 않거나(등록되지 않았거나) 이미 삭제(비활성화)되어
 * 입고 처리를 할 수 없을 때 던지는 예외.
 *
 * 참고: 이 프로젝트는 아직 GlobalExceptionHandler(@ControllerAdvice)가 없는 상태
 * (화면설계서 "공통 예외 정책"에 명시된 대로, 표준 예외 처리는 보안 결함 재현을 위해
 * 의도적으로 비어 있다가 나중에 추가될 예정 - REQ-NF-G-004).
 * 그래서 지금은 InventoryController에서 이 예외를 직접 try-catch로 잡아 처리.
 */
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(String message) {
        super(message);
    }
}
