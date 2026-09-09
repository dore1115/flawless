package com.dsa.flawless.service;

import com.dsa.flawless.domain.Item;
import com.dsa.flawless.domain.Stock;
import com.dsa.flawless.dto.ItemRowDto;
import com.dsa.flawless.dto.ItemDashboardSummaryDto;
import com.dsa.flawless.exception.DuplicateItemNameException;
import com.dsa.flawless.exception.ItemInUseException;
import com.dsa.flawless.exception.ItemNotFoundException;
import com.dsa.flawless.repository.ItemRepository;
import com.dsa.flawless.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * IV-001(품목 관리) 화면의 실제 처리를 담당하는 서비스 계층.
 * REQ-F-S-009(품목 등록)·REQ-F-S-010(품목 정보 수정)·REQ-F-C-008(품목 삭제)를 다룸.
 *
 * 재고 입고/출고를 다루는 InventoryService와 분리한 이유: 화면설계서 3-1 URL·Controller 목록표가
 * IV-001을 InventoryController가 아닌 별도의 ItemController로 명시하고 있어서(품목 CRUD와
 * 재고 입출고는 서로 다른 책임), 서비스 계층도 그 구분을 그대로 따름.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final StockRepository stockRepository;

    /**
     * IV-001 목록 조회.
     * 비활성화(삭제)된 품목은 DB에는 그대로 남지만 이 목록에는 아예 나타나지 않게 해달라는
     * 피드백에 맞춰, findAll(Pageable) 대신 findAllByDeletedFalse(Pageable)를 사용함
     */
    public Page<ItemRowDto> findAll(Pageable pageable) {
        return itemRepository.findAllByDeletedFalse(pageable)
                .map(item -> new ItemRowDto(item, getCurrentStockQuantity(item.getId())));
    }

    /**
     * 품목 하나의 상세(수정 검증 실패 시 모달 재표시용).
     */
    public ItemRowDto getRowOrThrow(Long id) {
        Item item = getItemOrThrow(id);
        return new ItemRowDto(item, getCurrentStockQuantity(id));
    }

    private int getCurrentStockQuantity(Long itemId) {
        // 아직 한 번도 입고되지 않은 품목은 재고 기록(Stock) 자체가 없을 수 있음(ERD 설명) → 0으로 취급
        return stockRepository.findByItemId(itemId).map(Stock::getQuantity).orElse(0);
    }

    private Item getItemOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("대상 품목이 없습니다. itemId=" + id));
    }

    /**
     * REQ-F-S-009(품목 등록).
     * 비고: "동일한 품목명으로 중복 등록은 허용하지 않음" → 저장 전에 미리 존재 여부를 확인.
     * 앞뒤 공백은 trim()으로 제거하고 비교/저장함 - 안 그러면 " 장갑"과 "장갑"이 서로 다른
     * 이름으로 취급돼 중복 검사를 우회할 수 있음(공백만 다른 사실상 같은 이름).
     * 기존 행을 이름으로 찾아 그 행이 삭제 상태면 그대로 재활성화(activate)하는 방식으로 구현함.
     *   1) 같은 이름의 "활성" 품목이 있으면 → 중복 등록 거부(기존 규칙 그대로)
     *   2) 같은 이름의 "삭제된" 품목이 있으면 → 새로 만들지 않고 그 행을 재활성화
     *   3) 같은 이름이 아예 없으면 → 새 품목으로 등록(기존 규칙 그대로)
     * 화면/사용자 입장에서는 세 경우 모두 "등록되었습니다"로 동일하게 보임(재활성화라는
     * 내부 동작을 굳이 드러내지 않음).
     */
    @Transactional
    public void create(String name) {
        String trimmedName = name.trim();
        Optional<Item> existing = itemRepository.findByName(trimmedName);
        if (existing.isPresent()) {
            Item item = existing.get();
            if (!item.isDeleted()) {
                throw new DuplicateItemNameException("이미 등록된 품목명입니다: " + trimmedName);
            }
            item.activate();
            return;
        }
        itemRepository.save(new Item(trimmedName));
    }

    /**
     * REQ-F-S-010(품목 정보 수정).
     * 비고: "이미 존재하는 다른 품목명으로는 변경할 수 없음(등록 시와 동일한 중복 검사)" → 자기
     * 자신은 제외하고 중복 확인. "재고 기록/이력과의 연결은 그대로 유지됨"은 별도 처리 불필요 -
     * rename()은 name 컬럼만 바꾸고 Stock/StockHistory는 item_id로 연결돼 있어 그대로 유지됨.
     */
    @Transactional
    public void update(Long id, String name) {
        Item item = getItemOrThrow(id);
        // create()와 동일하게 trim()으로 공백만 다른 이름을 같은 이름으로 취급함.
        String trimmedName = name.trim();
        if (itemRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateItemNameException("이미 등록된 품목명입니다: " + trimmedName);
        }
        item.rename(trimmedName);
        // JPA 변경 감지(dirty checking)로 트랜잭션 커밋 시 자동 UPDATE되므로 save() 호출 불필요.
    }

    /**
     * REQ-F-C-008(품목 삭제, 논리 삭제).
     * 비고: "현재 재고 수량이 0이 아니면 삭제할 수 없으며 오류 메시지가 표시됨."
     * "재고 정보 삭제와는 순서 상관없이 독립적으로 처리 가능"이라 Stock 자체는 건드리지 않고
     * item.is_deleted만 true로 바꿈(재고 기록과의 연결은 끊지 않음).
     */
    @Transactional
    public void delete(Long id) {
        Item item = getItemOrThrow(id);
        int currentQuantity = getCurrentStockQuantity(id);
        if (currentQuantity != 0) {
            throw new ItemInUseException("해당 재고 수량이 남아있어서 삭제할 수 없습니다. 현재 재고: " + currentQuantity + "개");
        }
        item.delete();
    }

    /**
     * 메인 페이지(index.html) 재고관리 카드 요약(활성 품목 수 / 재고 부족 건수).
     * 요구사항 문서에 메인 화면 자체가 없어서 카드에 필요한 최소한의 집계만 제공함.
     * "재고 부족"은 활성 품목 중 현재 재고 수량이 0인 것으로 정의함(품절 기준, 사용자 확인).
     */
    public ItemDashboardSummaryDto getDashboardSummary() {
        List<Item> activeItems = itemRepository.findAllByDeletedFalse();
        long lowStockCount = activeItems.stream()
                .filter(item -> getCurrentStockQuantity(item.getId()) == 0)
                .count();
        return new ItemDashboardSummaryDto(activeItems.size(), lowStockCount);
    }
}
