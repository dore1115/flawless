package com.dsa.flawless.service;

import com.dsa.flawless.domain.Item;
import com.dsa.flawless.dto.ItemRowDto;
import com.dsa.flawless.exception.DuplicateItemNameException;
import com.dsa.flawless.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IV-001(품목 관리) 화면의 실제 처리를 담당하는 서비스 계층.
 *
 * 지금은 REQ-F-S-009(품목 등록)만 구현하는 단계
 *
 * 재고 입고/출고를 다루는 InventoryService와 분리한 이유: 화면설계서 3-1 URL·Controller 목록표가
 * IV-001을 InventoryController가 아닌 별도의 ItemController로 명시하고 있어(품목 자체의 CRUD와
 * 재고 입출고 처리는 서로 다른 책임), 서비스 계층도 그 구분을 그대로 따름.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    /**
     * IV-001 목록 조회. 재고 조회 화면(IV-002, REQ-F-S-011 "삭제된 품목은 표시 안 함")과 달리,
     * 이 화면은 "관리" 화면이라 비활성화(삭제)된 품목도 함께 보여줘야 관리자가 상태를 확인/관리할 수
     * 있음 — 그래서 findAll(Pageable)을 그대로 사용.
     */
    public Page<ItemRowDto> findAll(Pageable pageable) {
        return itemRepository.findAll(pageable).map(ItemRowDto::new);
    }

    /**
     * REQ-F-S-009(품목 등록).
     * 비고: "동일한 품목명으로 중복 등록은 허용하지 않음" → 저장 전에 미리 존재 여부를 확인.
     */
    @Transactional
    public void create(String name) {
        if (itemRepository.existsByName(name)) {
            throw new DuplicateItemNameException("이미 등록된 품목명입니다: " + name);
        }
        itemRepository.save(new Item(name));
    }
}
