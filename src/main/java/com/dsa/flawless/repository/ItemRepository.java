package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Item(품목) 엔티티에 대한 데이터 접근 계층.
 * Spring Data JPA가 인터페이스 이름/메서드 이름만 보고 SQL을 자동으로 만들어 줌.
 * (예: findAllByDeletedFalse → "WHERE is_deleted = false" 조건의 SELECT를 자동 생성)
 */
public interface ItemRepository extends JpaRepository<Item, Long> {

    // 입고 화면(IV-003)의 "등록된 품목 목록" 드롭다운에 표시할, 삭제(비활성화)되지 않은 품목만 조회
    List<Item> findAllByDeletedFalse();

    // 입고 처리 시 품목 유효성 확인용: id가 존재하면서 동시에 삭제되지 않은 품목만 찾음
    // (삭제된 품목 id로 요청이 오면 여기서 걸러져 Optional.empty()가 반환됨)
    Optional<Item> findByIdAndDeletedFalse(Long id);
}
