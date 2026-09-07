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
     * 품목 등록/수정 시 "동일한 품목명으로 중복 등록/변경은 허용하지 않음"(요구사항정의서 비고) 확인용.
     * 삭제(비활성화) 여부와 무관하게 이름 자체의 유일성을 확인해야 하므로 deleted 조건 없이 전체 대상.
     */
    boolean existsByName(String name);

    // 품목 수정(update) 시 사용: 자기 자신은 제외하고 같은 이름을 쓰는 다른 품목이 있는지 확인
    // (그러지 않으면 품목명을 안 바꾸고 그대로 저장만 해도 "자기 자신과 중복"으로 걸림)
    boolean existsByNameAndIdNot(String name, Long id);
}
