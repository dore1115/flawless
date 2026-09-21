package com.dsa.flawless.service;

import com.dsa.flawless.dto.InboundHistoryDto;
import com.dsa.flawless.dto.InboundRequestDto;
import com.dsa.flawless.dto.OutboundHistoryDto;
import com.dsa.flawless.dto.OutboundItemOptionDto;
import com.dsa.flawless.dto.OutboundRequestDto;
import com.dsa.flawless.dto.StockHistoryRowDto;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Item;
import com.dsa.flawless.domain.MoveType;
import com.dsa.flawless.domain.Stock;
import com.dsa.flawless.domain.StockHistory;
import com.dsa.flawless.exception.InsufficientStockException;
import com.dsa.flawless.exception.ItemNotFoundException;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.repository.ItemRepository;
import com.dsa.flawless.repository.StockHistoryRepository;
import com.dsa.flawless.repository.StockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 입고 처리(REQ-F-M-005) 및 재고 출고 처리(REQ-F-M-006)의 실제 비즈니스 로직을
 * 담당하는 서비스 클래스. Controller는 "요청을 받아서 넘겨주는 역할"만 하고,
 * 실제 판단/저장 로직은 여기서 처리.
 *
 * @RequiredArgsConstructor: Lombok이 final 필드(itemRepository 등)를 매개변수로 받는
 * 생성자를 자동으로 만들어줌. 스프링이 그 생성자를 통해 의존성을 주입(DI).
 * (필드에 @Autowired를 직접 붙이는 방식보다 테스트하기 쉽고, 필드를 불변으로 만들 수 있어 권장되는 방식)
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ItemRepository itemRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    // 재고 입출고 이력 조회(IV-005)의 "처리자" 이름을 조회하기 위해 새로 주입함
    // (StockHistory는 employeeId 값만 갖고 있어서 이름을 보려면 Employee를 따로 조회해야 함).
    private final EmployeeRepository employeeRepository;

    /**
     * 입고 폼에 참고로 보여줄 "최근 입고 이력 5건"을 가져옴.
     * PageRequest.of(0, 5) → "0번째 페이지에서 5건만" 이라는 뜻으로,
     * 전체 이력을 다 가져오지 않고 DB에서 딱 5건만 조회(성능상 유리).
     */
    @Transactional(readOnly = true)
    public List<InboundHistoryDto> getRecentInboundHistories() {
        return stockHistoryRepository
                .findByMoveTypeOrderByCreatedAtDesc(MoveType.IN, PageRequest.of(0, 5))
                .stream()
                .map(InboundHistoryDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 재고 입고 처리 (REQ-F-M-005 / 화면설계서 IV-003 "처리 로직" 그대로 구현)
     *
     * 처리 순서:
     *   1) 품목이 실제 존재하고, 삭제(비활성화)되지 않았는지 확인
     *   2) 그 품목의 재고 기록(Stock)이 있는지 조회
     *      - 없으면 새로 만든다 (재고 기록은 "입고 처리"를 통해서만 최초 생성됨)
     *      - 있으면 기존 수량에 입력 수량만큼 더함
     *   3) 재고 수량 변경과 입출고 이력 저장을 "같은 트랜잭션"으로 처리
     *      → 화면설계서 경고 문구: "재고만 늘고 이력이 남지 않는 문제 방지"
     *
     * @Transactional: 이 메서드 안의 모든 DB 작업이 하나의 트랜잭션으로 묶임.
     * 즉, 재고 수량은 갱신됐는데 이력 저장에서 에러가 나는 경우 재고 갱신도 함께 롤백되어,
     * "재고는 늘었는데 이력이 없는" 불일치 상태가 발생하지 않음.
     *
     * @param request    입고 요청 정보(품목 id, 수량)
     * @param employeeId 처리한 사용자(물류관리자)의 사원 번호
     *                   - TODO: 현재는 로그인 기능이 아직 없어 Controller에서 임시값을 넣고 있음.
     *                     Spring Security 로그인 연동이 끝나면 세션의 실제 사용자 id로 교체 필요.
     */
    @Transactional
    public void inbound(InboundRequestDto request, Long employeeId) {

        // 1) 품목 유효성 확인 - 존재하지 않거나 이미 삭제된 품목이면 예외를 던져 처리를 중단.
        Item item = itemRepository.findByIdAndDeletedFalse(request.getItemId())
                .orElseThrow(() -> new ItemNotFoundException(
                        "등록되지 않았거나 비활성화된 품목입니다. itemId=" + request.getItemId()));

        // 2) 해당 품목의 재고 기록을 찾음.
        //    findByItemId 결과가 없으면(orElseGet) 수량 0짜리 새 Stock을 "메모리 상에서만" 만듦.
        //    (아직 stockRepository.save()를 호출하지 않았으므로 이 시점에는 DB에 아무것도 반영되지 않음)
        Stock stock = stockRepository.findByItemId(item.getId())
                .orElseGet(() -> new Stock(item, 0));

        // 재고 수량을 입력받은 수량만큼 늘림. (신규 생성이든 기존 재고든 동일한 코드로 처리됨)
        stock.increase(request.getQuantity());
        stockRepository.save(stock);

        // 3) 입출고 이력을 남김. 위 stock.save()와 이 save()는 같은 @Transactional 메서드
        //    안에서 실행되므로, 둘 다 성공해야만 최종적으로 커밋.
        StockHistory history = new StockHistory(item, MoveType.IN, request.getQuantity(), employeeId);
        stockHistoryRepository.save(history);
    }

    /*
     * ================================================================
     * 아래부터는 재고 출고 처리(REQ-F-M-006) 기능을 위해 새로 추가한 메서드임.
     * 입고 쪽 메서드(위)와 최대한 같은 구조/스타일로 작성해서 나중에 봤을 때
     * "입고랑 대칭되는 코드다"라는 걸 바로 알아볼 수 있게 함.
     * ================================================================
     */

    /**
     * 출고 폼(IV-004)의 "품목 선택" 목록을 가져옴. 품목마다 현재 재고 수량도 같이 담아 내려줌
     *
     * "재고 수량이 포함된 품목 옵션"이라는 뜻으로 입고/출고 둘 다에서 씀.
     *
     * 품목 하나하나마다 재고를 따로 조회하는 방식(N+1 조회)이라 데이터가 아주 많아지면
     * 느려질 수 있는 방식이지만, 지금 프로젝트 규모에서는 문제되지 않아 단순하게 구현함
     */
    @Transactional(readOnly = true)
    public List<OutboundItemOptionDto> getActiveItemOptionsWithStock() {
        return itemRepository.findAllByDeletedFalse().stream()
                .map(item -> {
                    // 재고 기록이 없는 품목은 0개로 취급(REQ-F-M-006: 출고는 재고 기록을 자동 생성하지 않음)
                    int currentQuantity = stockRepository.findByItemId(item.getId())
                            .map(Stock::getQuantity)
                            .orElse(0);
                    return new OutboundItemOptionDto(item, currentQuantity);
                })
                .collect(Collectors.toList());
    }

    /**
     * 출고 폼에 참고로 보여줄 "최근 출고 이력 5건"을 가져옴.
     * getRecentInboundHistories()와 똑같은 방식이고, MoveType만 IN 대신 OUT으로 바꿔서 조회함.
     */
    @Transactional(readOnly = true)
    public List<OutboundHistoryDto> getRecentOutboundHistories() {
        return stockHistoryRepository
                .findByMoveTypeOrderByCreatedAtDesc(MoveType.OUT, PageRequest.of(0, 5))
                .stream()
                .map(OutboundHistoryDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 재고 출고 처리 (REQ-F-M-006 / 화면설계서 IV-004 "처리 로직" 그대로 구현)
     *
     * 처리 순서:
     *   1) 품목이 실제 존재하고, 삭제(비활성화)되지 않았는지 확인
     *   2) 그 품목의 현재 재고 수량을 확인 (재고 기록이 없으면 0으로 취급)
     *   3) 요청 수량이 현재 재고보다 많으면 처리 거부(InsufficientStockException)
     *   4) 충분하면 재고 수량 차감 + 출고 이력 저장을 "같은 트랜잭션"으로 처리
     *
     * 입고 처리와의 핵심 차이(REQ-F-M-006 명세):
     *   - 입고는 재고 기록이 없으면 새로 만들지만, 출고는 재고 기록을 절대 새로 만들지 않음
     *     (재고가 아예 없는 품목은 항상 재고 부족으로 거부됨)
     *   - 입고는 수량 제한이 없지만, 출고는 "현재 재고 이하"라는 상한 제약이 있음
     *
     * @param request    출고 요청 정보(품목 id, 수량)
     * @param employeeId 처리한 사용자(물류관리자)의 사원 번호
     */
    @Transactional
    public void outbound(OutboundRequestDto request, Long employeeId) {

        // 1) 품목 유효성 확인 - 입고 처리와 동일한 방식
        Item item = itemRepository.findByIdAndDeletedFalse(request.getItemId())
                .orElseThrow(() -> new ItemNotFoundException(
                        "등록되지 않았거나 비활성화된 품목입니다. itemId=" + request.getItemId()));

        // 2) 현재 재고 수량 확인. 재고 기록 자체가 없으면 0개로 취급함(위 처리 순서 설명 참고).
        //! [REQ-NF-I-005] 아래 "조회(findByItemId) → 비교 → 차감(decrease) → 저장" 구간은 락이 없어서
        //! 동시 요청에 안전하지 않음 - 이 메서드 전체가 그 결함의 실제 발생 지점임. 증상과 해결 방법
        //! (낙관적/비관적 락)은 Stock.java의 주석 처리된 @Version 필드 위 주석에 자세히 적어둠.
        Stock stock = stockRepository.findByItemId(item.getId()).orElse(null);
        int currentQuantity = (stock != null) ? stock.getQuantity() : 0;

        // 3) 재고 부족 검사 - 요청 수량이 현재 재고보다 많으면 처리하지 않고 예외를 던져 중단.
        //    (요청 수량은 DTO의 @Min(1) 검증을 이미 통과했으므로 항상 1 이상이라,
        //     stock이 null인 경우(currentQuantity=0)는 이 조건에 항상 걸려 아래 stock.decrease()까지
        //     내려가지 않음 → NullPointerException 걱정 없이 안전하게 처리됨)
        if (currentQuantity < request.getQuantity()) {
            throw new InsufficientStockException(
                    "재고가 부족하여 출고할 수 없습니다. 현재 재고: " + currentQuantity + "개");
        }

        // 4) 재고 수량을 요청 수량만큼 줄이고 저장
        stock.decrease(request.getQuantity());
        stockRepository.save(stock);

        // 5) 출고 이력을 남김. 재고 차감과 같은 트랜잭션이라 하나가 실패하면 전부 롤백됨
        //    (입고 처리와 동일한 "재고-이력 불일치 방지" 원칙)
        StockHistory history = new StockHistory(item, MoveType.OUT, request.getQuantity(), employeeId);
        stockHistoryRepository.save(history);
    }

    /*
     * ================================================================
     * 아래부터는 재고 조회(IV-002, REQ-F-S-011) + 재고 검색(REQ-F-C-008) 기능을 위해
     * 새로 추가한 메서드임.
     * ================================================================
     */

    /**
     * IV-002 재고 조회 목록의 대상 품목을 가져옴(REQ-F-S-011). name이 있으면 재고 검색(REQ-F-C-008)까지
     * 함께 처리 - 삭제(비활성화)된 품목은 "재고 조회 목록에 표시되지 않음"(요구사항 비고) 조건 그대로
     * findAllByDeletedFalse 계열 쿼리를 씀.
     *
     */
    //! [REQ-NF-I-004] 해결 완료(2026-09-21): 요청한 페이지 크기만큼만 DB에서 가져오는 대신 조건에
    //! 맞는 전체 품목을 여기서 한 번에 다 가져와 InventoryController.list()가 자바 메모리 상
    //! subList()로 페이지를 잘라 쓰던 결함(페이지네이션 부재)을 고침. ItemController.list()와
    //! 같은 패턴으로, 이 메서드가 Pageable을 그대로 받아 리포지토리에 넘기고 Page<Item>을 돌려주도록
    //! 바꿔서 DB가 LIMIT/OFFSET을 처리하게 함(ItemRepository.findAllByDeletedFalseAndNameContaining
    //! (String, Pageable) 참고). 정렬(createdAt 오름차순)은 예전처럼 메서드 이름에 고정하는 대신
    //! 호출 측(InventoryController.list())이 Pageable에 Sort로 담아 넘김 - 품목 관리(IV-001)와
    //! 정렬 순서를 맞춰야 페이지별로 보이는 품목이 서로 어긋나지 않는다는 원래 의도는 그대로 유지됨.

    @Transactional(readOnly = true)
    public Page<Item> getFilteredActiveItems(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return itemRepository.findAllByDeletedFalse(pageable);
        }
        return itemRepository.findAllByDeletedFalseAndNameContaining(name.trim(), pageable);
    }

    /**
     * 위 getFilteredActiveItems()가 돌려준 품목들의 "현재 재고 수량"을 품목 id 기준으로 모아 담음.
     *
     */
    //! [REQ-NF-I-001] 해결 완료(2026-09-21): InventoryController.list()가 이 메서드의 결과(Map)를
    //! 받아 StockRowDto로 변환해서 Model에 담도록 고쳐짐(dto/StockRowDto.java 참고). 이 메서드
    //! 자체는 Map<Long,Integer>만 돌려주고 있었어서(Item 엔티티를 직접 넘기는 건 컨트롤러 쪽 문제였음)
    //! 그대로 둠.
    //!
    //! [REQ-NF-I-003] 해결 완료(2026-09-21): 품목 하나하나마다 stockRepository.findByItemId()를
    //! 개별 호출하던 N+1 쿼리를 StockRepository.findByItemIdIn()으로 교체해서 재고 행을 한 번의
    //! IN 절 쿼리로 모두 가져오도록 고침. 재고 기록이 아직 없는 품목(첫 입고 전)은 조회 결과에
    //! 안 나오므로 getOrDefault(...,0)으로 0개 처리함(기존 동작과 동일).
    //! ItemService.getDashboardSummary(), InventoryService.getActiveItemOptionsWithStock()에는
    //! 여전히 같은 패턴의 N+1이 남아있음 - 이번 수정 범위(재고 조회 화면)가 아니라 그대로 둠.

    @Transactional(readOnly = true)
    public Map<Long, Integer> getCurrentQuantities(List<Item> items) {
        List<Long> itemIds = items.stream()
                .map(Item::getId)
                .collect(Collectors.toList());

        // 재고 행이 있는 품목만 한 번의 쿼리로 가져와서 품목 id → 수량 맵으로 만듦.
        Map<Long, Integer> foundQuantities = stockRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(stock -> stock.getItem().getId(), Stock::getQuantity));

        // 요청받은 품목 순서를 그대로 유지하면서, 재고 행이 없는 품목(첫 입고 전)은 0개로 채움.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Item item : items) {
            quantities.put(item.getId(), foundQuantities.getOrDefault(item.getId(), 0));
        }
        return quantities;
    }

    /*
     * ================================================================
     * 아래부터는 재고 입출고 이력 조회(IV-005, REQ-F-S-012) 기능을 위해 새로 추가한 메서드임.
     * ================================================================
     */

    /**
     * IV-005 화면(품목별 입출고 이력 조회 페이지)에서 대상 품목의 이름 등을 보여주기 위해 사용.
     * ItemService.getItemOrThrow()와 같은 패턴 - 존재하지 않는 id로 접근하면 ItemNotFoundException.
     * 품목 관리(IV-001)에서 삭제(비활성화)한 품목이어도 과거 이력은 계속 조회할 수 있어야 하므로
     * findByIdAndDeletedFalse가 아닌 findById를 씀.
     */
    @Transactional(readOnly = true)
    public Item getItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("대상 품목이 없습니다. itemId=" + itemId));
    }

    /**
     * 특정 품목의 입출고 이력을 처리 일시 최신순으로 가져옴(REQ-F-S-012 "처리 일시 최신순으로
     * 정렬되어 표시된다"). "처리자 정보"까지 함께 보여줘야 해서, 이력들에 찍힌 employeeId를
     * 모아 한 번에(배치로) 사원 이름을 조회함
     */
    @Transactional(readOnly = true)
    public List<StockHistoryRowDto> getItemStockHistories(Long itemId) {
        List<StockHistory> histories = stockHistoryRepository.findByItemIdOrderByCreatedAtDesc(itemId);
        if (histories.isEmpty()) {
            return List.of();
        }

        Set<Long> employeeIds = histories.stream()
                .map(StockHistory::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, String> employeeNames = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        return histories.stream()
                .map(history -> new StockHistoryRowDto(
                        history,
                        employeeNames.getOrDefault(history.getEmployeeId(), "알 수 없음")))
                .collect(Collectors.toList());
    }
}
