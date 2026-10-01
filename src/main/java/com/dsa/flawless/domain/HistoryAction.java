package com.dsa.flawless.domain;

/**
 * 직책/부서 변경 이력({@link PositionHistory}, {@link DepartmentHistory})의 "동작 구분" 값.
 * schema.sql의 position_history.action / department_history.action 컬럼(VARCHAR(20))에 대응.
 *
 * MySQL의 ENUM 타입 대신 애플리케이션(자바) Enum + VARCHAR 저장 방식을 쓰는 이유는
 * {@link MoveType}과 동일 — MySQL ENUM은 값 추가 시 ALTER TABLE이 필요해 권장하지 않는다.
 *
 * REQ-NF-H-005 개선(감사 로그) 범위에서는 DELETE만 실제로 기록한다. H-005가 지적한 결함은
 * "삭제 이력(누가/언제/무엇을)이 기록되지 않음"이므로 우선 삭제(DELETE) 동작만 남기고,
 * 신설(CREATE)·변경(UPDATE) 이력까지 남기는 것은 이번 범위 밖으로 둔다 — 값 자체는 schema.sql에
 * 이미 준비돼 있으므로, 필요해지면 PositionService.create() 등에서 이 값으로 이력을 추가하면 된다.
 */
public enum HistoryAction {
    CREATE,
    UPDATE,
    DELETE
}
