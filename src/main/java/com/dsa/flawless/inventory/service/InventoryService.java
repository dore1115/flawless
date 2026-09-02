package com.dsa.flawless.inventory.service;

import com.dsa.flawless.inventory.dto.InboundHistoryDto;
import com.dsa.flawless.inventory.dto.InboundRequestDto;
import com.dsa.flawless.inventory.dto.ItemOptionDto;
import com.dsa.flawless.inventory.entity.Item;
import com.dsa.flawless.inventory.entity.MoveType;
import com.dsa.flawless.inventory.entity.Stock;
import com.dsa.flawless.inventory.entity.StockHistory;
import com.dsa.flawless.inventory.exception.ItemNotFoundException;
import com.dsa.flawless.inventory.repository.ItemRepository;
import com.dsa.flawless.inventory.repository.StockHistoryRepository;
import com.dsa.flawless.inventory.repository.StockRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 입고 처리(REQ-F-M-005)의 실제 비즈니스 로직을 담당하는 서비스 클래스.
 * Controller는 "요청을 받아서 넘겨주는 역할"만 하고, 실제 판단/저장 로직은 여기서 처리.
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

    /**
     * 입고 폼(IV-003)의 "품목 선택" 목록에 표시할, 삭제되지 않은 품목 목록을 가져옴.
     * @Transactional(readOnly = true): 조회 전용 트랜잭션임을 명시해 JPA가 변경 감지(dirty checking)
     * 등의 불필요한 작업을 생략하도록 해 약간의 성능 이점을 얻음.
     */
    @Transactional(readOnly = true)
    public List<ItemOptionDto> getActiveItemOptions() {
        return itemRepository.findAllByDeletedFalse().stream()
                .map(ItemOptionDto::new)
                .collect(Collectors.toList());
    }

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
}
