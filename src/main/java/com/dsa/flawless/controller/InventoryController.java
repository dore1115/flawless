package com.dsa.flawless.controller;

import com.dsa.flawless.dto.InboundRequestDto;
import com.dsa.flawless.dto.OutboundRequestDto;
import com.dsa.flawless.dto.StockHistoryRowDto;
import com.dsa.flawless.dto.StockRowDto;
import com.dsa.flawless.exception.InsufficientStockException;
import com.dsa.flawless.exception.ItemNotFoundException;
import com.dsa.flawless.service.InventoryService;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 재고 관련 화면/처리를 담당하는 컨트롤러.
 *
 * 화면설계서 3-1. URL·Controller 목록 표 기준:
 *   GET  /inventory/items        → list()          → inventory/list.html
 *   GET  /inventory/inbound      → inboundForm()   → inventory/inbound.html
 *   POST /inventory/inbound      → inbound()       → 처리 후 재고 조회(IV-002, /inventory/items)로 리다이렉트
 *   GET  /inventory/outbound     → outboundForm()  → inventory/outbound.html
 *   POST /inventory/outbound     → outbound()      → 처리 후 재고 조회(IV-002, /inventory/items)로 리다이렉트
 *   GET  /inventory/{id}/history → history()       → inventory/history.html
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 빈 값이면 그냥 null로 처리해서, 원래 있던 "품목을 선택해 주세요." 메시지가 뜨게 함.
     */
    @InitBinder("inboundRequestDto")
    public void initInboundBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, "itemId", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.isBlank()) {
                    setValue(null);
                } else {
                    setValue(Long.valueOf(text));
                }
            }
        });
    }

    /**
     * IV-003 재고 입고 폼 화면을 보여줌.
     * itemOptions(품목 선택 목록)와 recentInbound(최근 입고 이력 5건)를 함께 내려줌.
     */
    @GetMapping("/inbound")
    public String inboundForm(Model model) {
        model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
        model.addAttribute("recentInbound", inventoryService.getRecentInboundHistories());

        // 검증 실패로 이 화면으로 다시 돌아오는 경우가 아니라면(=최초 진입),
        // 폼에 바인딩할 빈 DTO를 새로 만들어 넣어줌. (th:object로 사용하기 위해 필요)
        if (!model.containsAttribute("inboundRequestDto")) {
            model.addAttribute("inboundRequestDto", new InboundRequestDto());
        }
        return "inventory/inbound";
    }

    /**
     * IV-003 재고 입고 처리.
     *
     * 화면설계서 "분기·실패 처리" 표 그대로 구현:
     *   - 품목 미선택/미존재            → 화면 유지 + 필드 오류
     *   - 수량 0 이하 또는 정수가 아님   → 화면 유지 + "1 이상의 수량을 입력해 주세요."
     *   - 성공                          → redirect + 플래시 메시지 "입고 처리가 완료되었습니다."
     *
     * @Valid: InboundRequestDto에 붙인 @NotNull, @Min 등의 검증 애노테이션을 실행.
     * BindingResult: @Valid 바로 다음 파라미터로 와야 하며, 검증 실패 결과가 예외 대신 여기에 담김.
     *                (BindingResult가 없으면 검증 실패 시 400 에러 페이지로 바로 튕겨져 나감)
     */
    @PostMapping("/inbound")
    public String inbound(@Valid @ModelAttribute("inboundRequestDto") InboundRequestDto requestDto,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {

        // 1) 입력 형식 검증(수량 1 이상 등) 실패 → 폼 화면을 유지하면서 필드 오류를 함께 보여줌.
        if (bindingResult.hasErrors()) {
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
            model.addAttribute("recentInbound", inventoryService.getRecentInboundHistories());
            return "inventory/inbound";
        }

        //! [REQ-NF-I-002] 비기능요구사항 결함: "품목 등록/수정/삭제, 재고 정보 삭제" API에 물류관리자 권한 검증이 빠져 있었음
        //!   - 해결 완료(2026-09-17): SecurityConfig.securityFilterChain()에 POST /inventory,
        //!     POST /inventory/*, POST /inventory/*/delete를 hasRole("ADMIN_IV")로 제한함.
        //!   - 남은 별개 문제: 이 메서드는 로그인 사원이 누구인지 조회하지 않고 employeeId를 1로
        //!     고정해둔 상태임(출고 처리는 이미 로그인 사원을 조회하도록 되어 있음 - 아래 outbound()
        //!     메서드 참고). 이건 REQ-NF-I-002(Authority 미비)가 아니라 별개의 품질 이슈라 이번
        //!     수정 범위에서는 건드리지 않음.
        Long employeeId = 1L;

        try {
            inventoryService.inbound(requestDto, employeeId);
        } catch (ItemNotFoundException e) {
            // 2) 품목이 없거나 비활성화된 경우 → itemId 필드에 오류를 등록하고 폼 화면을 유지.
            bindingResult.rejectValue("itemId", "invalid", "품목을 선택해 주세요.");
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
            model.addAttribute("recentInbound", inventoryService.getRecentInboundHistories());
            return "inventory/inbound";
        }

        // 3) 성공 시: 입고 화면 자기 자신으로 되돌아가 배너로 완료 메시지를 보여줌.
        //    [되돌림] 한 번은 재고 조회(IV-002) 화면으로 리다이렉트하도록 바꿨었는데, 입고 처리
        //    후에는 입고 화면에 그대로 남아있는 게 낫다는 피드백을 받아 원래 방식(자기 자신 redirect)으로
        //    되돌림. 출고 처리(outbound())는 이 피드백 대상이 아니라서 그대로 재고 조회로 리다이렉트함.
        //    addFlashAttribute는 리다이렉트 "다음 한 번의 요청"에만 살아있는 메시지를 전달할 때 사용.
        redirectAttributes.addFlashAttribute("message", "입고 처리가 완료되었습니다.");
        return "redirect:/inventory/inbound";
    }

    /*
     * ================================================================
     * 아래부터는 재고 출고 처리(REQ-F-M-006) 기능을 위해 새로 추가한 부분.
     * 위 입고 컨트롤러 메서드 두 개와 최대한 같은 구조로 작성함.
     * ================================================================
     */

    /**
     * 빈 값이면 그냥 null로 처리해서, 원래 있던 "품목을 선택해 주세요." 메시지가 뜨게 함.
     */
    @InitBinder("outboundRequestDto")
    public void initOutboundBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, "itemId", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.isBlank()) {
                    setValue(null);
                } else {
                    setValue(Long.valueOf(text));
                }
            }
        });
    }

    /**
     * IV-004 재고 출고 폼 화면을 보여줌.
     * itemOptions(현재 재고 수량이 함께 담긴 품목 선택 목록)와 recentOutbound(최근 출고 이력 5건)를
     * 함께 내려줌.
     */
    @GetMapping("/outbound")
    public String outboundForm(Model model) {
        model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
        model.addAttribute("recentOutbound", inventoryService.getRecentOutboundHistories());

        if (!model.containsAttribute("outboundRequestDto")) {
            model.addAttribute("outboundRequestDto", new OutboundRequestDto());
        }
        return "inventory/outbound";
    }

    /**
     * IV-004 재고 출고 처리.
     *
     * 화면설계서 "분기·실패 처리" 표 그대로 구현:
     *   - 품목 미선택/미존재            → 화면 유지 + 필드 오류
     *   - 수량 0 이하 또는 정수가 아님   → 화면 유지 + "1 이상의 수량을 입력해 주세요."
     *   - 재고 부족                     → 화면 유지 + "재고가 부족하여 출고할 수 없습니다..." 오류
     *   - 성공                          → redirect + 플래시 메시지 "출고 처리가 완료되었습니다."
     */
    @PostMapping("/outbound")
    public String outbound(@Valid @ModelAttribute("outboundRequestDto") OutboundRequestDto requestDto,
                            BindingResult bindingResult,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes,
                            Model model) {

        // 1) 입력 형식 검증(수량 1 이상 등) 실패 → 폼 화면을 유지하면서 필드 오류를 함께 보여줌.
        if (bindingResult.hasErrors()) {
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
            model.addAttribute("recentOutbound", inventoryService.getRecentOutboundHistories());
            return "inventory/outbound";
        }

        // 현재 로그인한 사용자의 username(authentication.getName())으로 실제 사원을 조회해서 사용함.
        Long employeeId = inventoryService.getEmployeeIdByUsername(authentication.getName());

        //! [REQ-NF-I-002] 해결 완료(2026-09-17): SecurityConfig에서 POST /inventory/outbound를
        //! hasRole("ADMIN_IV")로 제한해서, 여기 도달하는 시점엔 이미 물류관리자로 확인된 상태임
        //! (자세한 내용은 위 inbound() 메서드의 employeeId 선언부 주석 참고).

        try {
            inventoryService.outbound(requestDto, employeeId);
        } catch (ItemNotFoundException e) {
            // 2) 품목이 없거나 비활성화된 경우 → itemId 필드에 오류를 등록하고 폼 화면을 유지.
            bindingResult.rejectValue("itemId", "invalid", "품목을 선택해 주세요.");
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
            model.addAttribute("recentOutbound", inventoryService.getRecentOutboundHistories());
            return "inventory/outbound";
        } catch (InsufficientStockException e) {
            // 3) 재고 부족 → quantity 필드에 오류를 등록하고 폼 화면을 유지.
            //    e.getMessage()에 "현재 재고: N개"까지 담겨 있어 그대로 화면에 보여줌.
            bindingResult.rejectValue("quantity", "invalid", e.getMessage());
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptionsWithStock());
            model.addAttribute("recentOutbound", inventoryService.getRecentOutboundHistories());
            return "inventory/outbound";
        }

        // 4) 성공 시: 위 inbound()와 동일한 이유로(피드백에 따라 재고 조회 화면 대신 자기 자신인
        //    출고 화면으로 되돌아가도록 되돌림), 출고 화면 자기 자신으로 되돌아가 배너로 완료 메시지를 보여줌.
        redirectAttributes.addFlashAttribute("message", "출고 처리가 완료되었습니다.");
        return "redirect:/inventory/outbound";
    }

    /*
     * ================================================================
     * 아래부터는 재고 조회(IV-002) 기능을 위해 새로 추가한 부분.
     * ================================================================
     */

    // 한 페이지에 보여줄 품목 수.
    private static final int STOCK_PAGE_SIZE = 10;

    /**
     * IV-002 재고 조회 화면(REQ-F-S-011). name이 있으면 재고 검색(REQ-F-C-008)까지 함께 처리함.
     */
    //! [REQ-NF-I-001] 해결 완료(2026-09-21): 이 메서드가 Item 엔티티를 변환 없이 그대로 Model에
    //! 담아 템플릿에 넘기던 결함을 수정함. StockRowDto(dto/StockRowDto.java 참고)로 변환해서
    //! 품목명/현재 수량만 담아 넘기도록 바꿨고, deleted/createdAt/updatedAt 같은 내부 필드는
    //! 더 이상 템플릿에서 접근할 수 없음. InventoryService.getCurrentQuantities()의 N+1 쿼리
    //! (REQ-NF-I-003)는 이번 수정 범위가 아니라 그대로 둠.
    //! [REQ-NF-I-004] 해결 완료(2026-09-21): DB에는 Pageable 없이 조건에 맞는 전체 품목을 한 번에
    //! 다 가져오게 해두고 여기서 자바 메모리 상 subList()로 요청 페이지만 잘라내던 결함(페이지네이션
    //! 부재)을 고침. ItemController.list()와 같은 패턴으로 PageRequest(Pageable)를 만들어
    //! InventoryService.getFilteredActiveItems()에 그대로 넘기고, DB가 LIMIT/OFFSET으로 걸러준
    //! Page<Item> 결과를 그대로 씀 - currentPage/totalPages도 Page 객체가 계산해 주는 값을 그대로
    //! 꺼내 쓰면 돼서, 기존에 직접 계산하던 코드(Math.ceil/subList 등)가 전부 없어짐. 정렬은 기존과
    //! 동일하게 createdAt 오름차순을 유지(품목 관리(IV-001)와 페이지별 순서를 맞추기 위함 - 위
    //! getFilteredActiveItems() 주석 참고).

    @GetMapping("/items")
    public String list(@RequestParam(required = false) String name,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Pageable pageable = PageRequest.of(page, STOCK_PAGE_SIZE, Sort.by("createdAt").ascending());
        Page<Item> matchedPage = inventoryService.getFilteredActiveItems(name, pageable);
        List<Item> pagedItems = matchedPage.getContent();

        Map<Long, Integer> quantities = inventoryService.getCurrentQuantities(pagedItems);
        List<StockRowDto> stockRows = pagedItems.stream()
                .map(item -> new StockRowDto(item, quantities.getOrDefault(item.getId(), 0)))
                .toList();

        model.addAttribute("items", stockRows);
        model.addAttribute("keyword", name);
        model.addAttribute("currentPage", matchedPage.getNumber());
        model.addAttribute("totalPages", Math.max(matchedPage.getTotalPages(), 1));
        return "inventory/list";
    }

    /*
     * ================================================================
     * 아래부터는 재고 입출고 이력 조회(IV-005) 기능을 위해 새로 추가한 부분.
     * ================================================================
     */

    /**
     * IV-005 재고 입출고 이력 조회(REQ-F-S-012). 재고 조회(IV-002) 목록에서 품목명을
     * 누르면 이 화면으로 이동하고, "재고 조회로 돌아가기" 링크로 다시 IV-002로 돌아감(비고:
     * "이력은 조회만 가능하며 수정·삭제할 수 없다" → 그래서 이 화면엔 조회용 GET 매핑만 있음).
     */
    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id, Model model) {
        StockRowDto item = inventoryService.getStockRowOrThrow(id);
        List<StockHistoryRowDto> histories = inventoryService.getItemStockHistories(id);

        model.addAttribute("item", item);
        model.addAttribute("histories", histories);
        return "inventory/history";
    }
}
