package com.dsa.flawless.repository;

import com.dsa.flawless.domain.PositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PositionHistory(직책 변경 이력) 엔티티에 대한 데이터 접근 계층 (REQ-NF-H-005 개선: 감사 로그).
 * 현재는 삭제 시 append(저장)만 필요하므로 별도 조회 메서드는 두지 않는다 — 이력 조회 화면이
 * 추가되면(스코프 확장 시) findByPositionIdOrderByCreatedAtDesc 등을 여기에 추가하면 된다.
 */
public interface PositionHistoryRepository extends JpaRepository<PositionHistory, Long> {
}
