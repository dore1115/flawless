package com.dsa.flawless.inventory.controller;

import com.dsa.flawless.inventory.dto.InboundRequestDto;
import com.dsa.flawless.inventory.exception.ItemNotFoundException;
import com.dsa.flawless.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 재고 관련 화면/처리를 담당하는 컨트롤러.
 * 지금은 "재고 입고 처리(IV-003)" 기능만 구현.
 * (품목 관리 IV-001, 재고 조회 IV-002, 출고 IV-004, 이력 IV-005는 이후 별도 작업)
 *
 * 화면설계서 3-1. URL·Controller 목록 표 기준:
 *   GET  /inventory/inbound  → inboundForm()  → inventory/inbound.html
 *   POST /inventory/inbound  → inbound()      → 처리 후 재고 조회(IV-002, /inventory/items)로 리다이렉트
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

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
}
