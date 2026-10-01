package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Item(품목) 엔티티에 대한 데이터 접근 계층.
 * Spring Data JPA가 인터페이스 이름/메서드 이름만 보고 SQL을 자동으로 만들어 줌.
 * (예: findAllByDeletedFalse → "WHERE is_deleted = false" 조건의 SELECT를 자동 생성)
 */
public interface ItemRepository extends JpaRepository<Item, Long> {

    // 입고 화면(IV-003)의 "등록된 품목 목록" 드롭다운에 표시할, 삭제(비활성화)되지 않은 품목만 조회
    List<Item> findAllByDeletedFalse();

    // IV-001(품목 관리) 목록 조회용. 비활성화(삭제)된 품목을 페이지 목록 자체에서 보이지 않게 함
    Page<Item> findAllByDeletedFalse(Pageable pageable);

    // 입고 처리 시 품목 유효성 확인용: id가 존재하면서 동시에 삭제되지 않은 품목만 찾음
    Optional<Item> findByIdAndDeletedFalse(Long id);

    // 품목 등록(create) 시 사용: 이름으로 기존 품목을 찾음(삭제 여부와 무관하게 전체 대상).
    // "동일한 품목명으로 중복 등록은 허용하지 않음" 확인과, "삭제된 품목명은
    // 재사용 가능하게"(피드백) 요청을 함께 처리하기 위해 씀 - 결과가 있는데 삭제 상태가 아니면
    // 중복 거부, 삭제 상태면 그 행을 재활성화, 결과가 없으면 새로 등록(ItemService.create() 참고).
    // name은 DB에서 unique 제약이라(Item.java 참고) 새 행을 따로 insert하는 방식으로는
    // "삭제된 이름 재사용"을 구현할 수 없어서, existsByName() 대신 이 메서드로 교체함.
    Optional<Item> findByName(String name);

    // 품목 수정(update) 시 사용: 자기 자신은 제외하고 같은 이름을 쓰는 다른 품목이 있는지 확인
    boolean existsByNameAndIdNot(String name, Long id);

    // 재고 조회(IV-002) 화면의 검색(REQ-F-C-008 재고 검색)용: 활성 품목 중 이름에 검색어가
    // 포함된 것만 조회.
    // //! [REQ-NF-I-004] 참고: 아래 List 반환 메서드 2개(findAllByDeletedFalseOrderByCreatedAtAsc,
    // findAllByDeletedFalseAndNameContainingOrderByCreatedAtAsc)는 전체 데이터를 한 번에 가져오던
    // 옛 구현(InventoryService.getFilteredActiveItems())이 쓰던 것으로, 그 결함을 고치면서 더 이상
    // 쓰이지 않게 됨. 대신 아래 Page 반환 메서드로 대체함. 다른 곳에서 쓰는지 확인 후 문제 없으면
    // 다음 정리 때 삭제 가능.
    List<Item> findAllByDeletedFalseAndNameContaining(String name);

    // 재고 조회(IV-002) 화면이 품목 관리(IV-001)와 정렬 순서가 달라서(품목 관리는
    // createdAt 오름차순 정렬, 재고 조회는 정렬 조건 없이 DB가 임의로 주는 순서를 그대로 씀 -
    // 재활성화된 품목처럼 createdAt과 id 순서가 어긋난 품목이 있으면 두 화면 페이지가 서로
    // 다르게 보임) 위 두 메서드와 결과는 같지만 정렬만 보장하는 버전을 새로 추가함
    List<Item> findAllByDeletedFalseOrderByCreatedAtAsc();

    List<Item> findAllByDeletedFalseAndNameContainingOrderByCreatedAtAsc(String name);

    //! [REQ-NF-I-004] 해결: 재고 조회(IV-002) 화면이 findAll류로 조건에 맞는 전체 품목을
    //! 한 번에 다 가져온 뒤 애플리케이션 메모리에서 페이지를 잘라 쓰던 결함(페이지네이션 부재)을
    //! 고치기 위해 추가한 메서드. ItemController.list()가 쓰는 findAllByDeletedFalse(Pageable)
    //! (위 파일 상단)과 같은 패턴으로, DB가 LIMIT/OFFSET을 처리하도록 Pageable을 그대로 넘김.
    //! 정렬은 호출 측(InventoryService.getFilteredActiveItems())에서 Pageable에 담아 넘기는
    //! Sort(createdAt 오름차순)를 그대로 따름 - 위 findAllByDeletedFalseOrderByCreatedAtAsc()가
    //! 메서드 이름에 정렬을 고정해둔 것과 달리, 여기서는 Pageable의 Sort가 그 역할을 대신함.
    Page<Item> findAllByDeletedFalseAndNameContaining(String name, Pageable pageable);
}
