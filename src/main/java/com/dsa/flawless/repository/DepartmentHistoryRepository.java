package com.dsa.flawless.repository;

import com.dsa.flawless.domain.DepartmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * DepartmentHistory(부서 변경 이력) 엔티티에 대한 데이터 접근 계층 (REQ-NF-H-005 개선: 감사 로그).
 * PositionHistoryRepository와 동일한 이유로 현재는 조회 메서드를 두지 않는다.
 */
public interface DepartmentHistoryRepository extends JpaRepository<DepartmentHistory, Long> {
}
