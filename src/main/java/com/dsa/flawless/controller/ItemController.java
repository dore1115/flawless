package com.dsa.flawless.controller;

import com.dsa.flawless.dto.ItemCreateForm;
import com.dsa.flawless.exception.DuplicateItemNameException;
import com.dsa.flawless.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * IV-001(품목 관리, GET /inventory)을 처리. InventoryController(입고/출고)와는 별도 컨트롤러.
 *   GET  /inventory  → list()   → inventory/item/list.html
 *   POST /inventory  → create() → (리다이렉트)
 *
 * [REQ-NF-I-002] 품목 등록은 화면설계서상 "물류관리자" 전용인데 인가 검증이 없음
 * - 로그인만 되어 있으면 아무 사원이나 등록 가능. 해결 방법 요약: (1) 로그인 사원의
 * Role을 조회해 물류관리자가 아니면 거부, 또는 (2) REQ-NF-G-003(전역 인가) 해결 후 SecurityConfig에서
 * 이 경로를 hasRole("LOGISTICS_MANAGER")로 제한. 자세한 설명은 InventoryController.inbound()의
 * employeeId 선언부 주석 참고(같은 결함, 같은 해결 방법).
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class ItemController {

    // 문서에 페이지당 표시 건수가 정해져 있지 않아, 화면 시안의 페이징 UI에 맞춰 임의로 정한 값.
    private static final int PAGE_SIZE = 10;

    private final ItemService itemService;

    /**
     * IV-001 품목 목록 화면.
     * 등록 모달의 폼 객체(itemCreateForm)는 검증 실패로 이 화면에 다시 돌아온 경우가 아니라면
     * (=최초 진입) 여기서 빈 값으로 새로 만들어 넣음.
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        model.addAttribute("items", itemService.findAll(pageable));

        if (!model.containsAttribute("itemCreateForm")) {
            model.addAttribute("itemCreateForm", new ItemCreateForm());
        }
        return "inventory/item/list";
    }

    /**
     * REQ-F-S-009 품목 등록("새 품목 등록" 모달의 "등록" 버튼).
     *
     * 분기 처리:
     *   - 품목명 공백(@NotBlank 검증 실패)  → 목록 화면 유지 + 등록 모달을 연 채로 필드 오류 표시
     *   - 이미 등록된 품목명(중복)          → 목록 화면 유지 + 등록 모달을 연 채로 필드 오류 표시
     *   - 성공                             → redirect + 플래시 메시지 "품목이 등록되었습니다."
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("itemCreateForm") ItemCreateForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {

        if (!bindingResult.hasErrors()) {
            try {
                itemService.create(form.getName());
                redirectAttributes.addFlashAttribute("message", "품목이 등록되었습니다.");
                return "redirect:/inventory";
            } catch (DuplicateItemNameException e) {
                bindingResult.rejectValue("name", "duplicate", e.getMessage());
            }
        }

        // 실패 시: 목록을 다시 채워서 같은 화면으로 돌아가되, 등록 모달이 열린 채로 보이도록 함.
        model.addAttribute("items", itemService.findAll(PageRequest.of(0, PAGE_SIZE, Sort.by("id").ascending())));
        model.addAttribute("openRegisterModal", true);
        return "inventory/item/list";
    }
}
