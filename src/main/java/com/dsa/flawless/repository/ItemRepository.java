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

    /*
     * [품목 등록(REQ-F-S-009) 기능 추가 시 새로 추가한 메서드]
     * "동일한 품목명으로 중복 등록은 허용하지 않음"(요구사항정의서 비고)을 확인하기 위한 중복 검사용.
     * 삭제(비활성화) 여부와 무관하게 이름 자체의 유일성을 확인해야 하므로(item 테이블의 UNIQUE 제약도
     * deleted 여부를 따지지 않음) deleted 조건 없이 전체를 대상으로 함.
     */
    boolean existsByName(String name);
}
