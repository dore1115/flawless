package com.dsa.flawless.controller;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.dto.InboundRequestDto;
import com.dsa.flawless.dto.OutboundRequestDto;
import com.dsa.flawless.exception.InsufficientStockException;
import com.dsa.flawless.exception.ItemNotFoundException;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.service.InventoryService;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 재고 관련 화면/처리를 담당하는 컨트롤러.
 * 지금은 "재고 입고 처리(IV-003)"와 "재고 출고 처리(IV-004)" 기능이 구현되어 있음.
 * (품목 관리 IV-001, 재고 조회 IV-002, 이력 IV-005는 이후 별도 작업)
 *
 * 화면설계서 3-1. URL·Controller 목록 표 기준:
 *   GET  /inventory/inbound   → inboundForm()   → inventory/inbound.html
 *   POST /inventory/inbound   → inbound()       → 처리 후 재고 조회(IV-002, /inventory/items)로 리다이렉트
 *   GET  /inventory/outbound  → outboundForm()  → inventory/outbound.html
 *   POST /inventory/outbound  → outbound()      → 처리 후 재고 조회(IV-002, /inventory/items)로 리다이렉트
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // [출고 기능(REQ-F-M-006) 추가 시 새로 주입함]
    // 이유: "처리한 사원"을 기록하려면 지금 로그인한 사원의 id가 필요한데, 로그인 기능이
    //      완성되면서 EmployeeRepository로 실제 로그인한 사원을 조회할 수 있게 됐음.
    //      그래서 출고 처리부터는 예전 입고 처리처럼 employeeId를 1로 고정하지 않고,
    //      실제 로그인한 사원의 id를 사용함(아래 outbound() 메서드 참고).
    private final EmployeeRepository employeeRepository;

    /**
     * [버그 수정] 품목을 선택 안 하고 입고 처리를 누르면 빈 값이 그대로 넘어오는데,
     * itemId가 Long 타입이라 빈 값을 숫자로 못 바꿔서 오류 화면이 뜨던 문제를 고침.
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
        model.addAttribute("itemOptions", inventoryService.getActiveItemOptions());
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
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptions());
            model.addAttribute("recentInbound", inventoryService.getRecentInboundHistories());
            return "inventory/inbound";
        }

        // TODO(권한 확인): 화면설계서상 이 화면은 "물류관리자 전용"이지만,
        // 이 저장소에는 아직 로그인/Employee/Spring Security 인가 설정이 없음.
        // REQ-NF-I-002(권한 검증 부재)에 해당하는 부분으로, 로그인 기능이 합쳐지면
        // 아래 employeeId를 세션의 실제 사용자 id로 바꾸고, 물류관리자가 아니면 403을 반환하도록
        // 인가 로직 추가 필요. 지금은 기능 동작 확인을 위한 임시값을 작성해둠.
        Long employeeId = 1L;

        try {
            inventoryService.inbound(requestDto, employeeId);
        } catch (ItemNotFoundException e) {
            // 2) 품목이 없거나 비활성화된 경우 → itemId 필드에 오류를 등록하고 폼 화면을 유지.
            bindingResult.rejectValue("itemId", "invalid", "품목을 선택해 주세요.");
            model.addAttribute("itemOptions", inventoryService.getActiveItemOptions());
            model.addAttribute("recentInbound", inventoryService.getRecentInboundHistories());
            return "inventory/inbound";
        }

        // 3) 성공 시: 화면설계서 화면 흐름도에 따라 "재고 조회(IV-002, /inventory/items)"로 이동.
        //    addFlashAttribute는 리다이렉트 "다음 한 번의 요청"에만 살아있는 메시지를 전달할 때 사용.
        redirectAttributes.addFlashAttribute("message", "입고 처리가 완료되었습니다.");
        return "redirect:/inventory/items";
    }

    /*
     * ================================================================
     * 아래부터는 재고 출고 처리(REQ-F-M-006) 기능을 위해 새로 추가한 부분임.
     * 위 입고 컨트롤러 메서드 두 개와 최대한 같은 구조로 작성함.
     * ================================================================
     */

    /**
     * [버그 수정] 품목을 선택 안 하고 출고 처리를 누르면 빈 값이 그대로 넘어오는데,
     * itemId가 Long 타입이라 빈 값을 숫자로 못 바꿔서 오류 화면이 뜨던 문제를 고침.
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

        // 로그인 기능이 이제 완성되어 있어서, 입고 처리 때처럼 employeeId를 1로 고정하지 않고
        // 현재 로그인한 사용자의 username(authentication.getName())으로 실제 사원을 조회해서 사용함.
        Employee employee = employeeRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "로그인 정보에 해당하는 사원을 찾을 수 없습니다. username=" + authentication.getName()));
        Long employeeId = employee.getId();

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

        // 4) 성공 시: 입고 처리와 동일하게 재고 조회(IV-002, /inventory/items)로 이동.
        redirectAttributes.addFlashAttribute("message", "출고 처리가 완료되었습니다.");
        return "redirect:/inventory/items";
    }
}
