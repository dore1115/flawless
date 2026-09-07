package com.dsa.flawless.domain;

/**
 * 재고 입출고 이력(stock_history)의 "구분" 값.
 * ERD의 stock_history.move_type 컬럼(VARCHAR(20))에 대응.
 *
 * MySQL의 ENUM 타입 대신 애플리케이션(자바) Enum + VARCHAR 저장 방식을 쓰는 이유는
 * ERD 공통 설계 규칙에 "MySQL ENUM은 값 추가 시 ALTER TABLE이 필요해 권장하지 않는다"고
 * 명시되어 있기 때문.
 */
public enum MoveType {
    IN,  // 입고
    OUT  // 출고 (재고 출고 처리, REQ-F-M-006에서 사용)
}
