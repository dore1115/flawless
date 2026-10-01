package com.dsa.flawless.controller;

import com.dsa.flawless.dto.ItemCreateForm;
import com.dsa.flawless.dto.ItemUpdateForm;
import com.dsa.flawless.exception.DuplicateItemNameException;
import com.dsa.flawless.exception.ItemInUseException;
import com.dsa.flawless.exception.ItemNotFoundException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * IV-001(품목 관리, GET /inventory)을 처리. InventoryController(입고/출고)와는 별도 컨트롤러.
 *   GET  /inventory          → list()   → inventory/item/list.html
 *   POST /inventory          → create() → (리다이렉트)
 *   POST /inventory/{id}     → update() → (리다이렉트)
 *   POST /inventory/{id}/delete → delete() → (리다이렉트)
 * 삭제(REQ-F-C-008)는 화면설계서 Controller 표에 행 자체가 없어(등록/수정만 기재됨) 부서/직책
 * 삭제와 같은 "POST /{id}/delete" 규칙을 그대로 따라 새로 정의함.
 */
//! [REQ-NF-I-002] 품목 등록/수정/삭제는 화면설계서상 "물류관리자" 전용인데 인가 검증이 없었음
//! - 해결 완료(2026-09-17): SecurityConfig.securityFilterChain()에서 POST /inventory,
//! POST /inventory/*, POST /inventory/*/delete를 hasRole("ADMIN_IV")로 제한함
//! (역할명 ADMIN_IV는 Role.java 기준 확정, 이전 주석의 "LOGISTICS_MANAGER"는 가칭이었음).
//! 자세한 설명은 InventoryController.inbound()의 employeeId 선언부 주석 참고
//! (입고/출고도 같은 규칙으로 함께 제한함).
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class ItemController {

    // 문서에 페이지당 표시 건수가 정해져 있지 않아, 화면 시안의 페이징 UI에 맞춰 임의로 정한 값.
    private static final int PAGE_SIZE = 10;

    // 원래 id 오름차순으로 정렬했으나, 삭제 후 재등록은 새 행을 insert하지 않고
    // 기존 행을 재활성화하는 방식이라(ItemService.create() 참고) id 자체가 그대로라 id 정렬로는
    // 항상 원래 위치에 남게 됨.
    //   - Item.activate()가 재활성화될 때만 createdAt을
    //     지금 시각으로 다시 채워주므로(Item.java/BaseTimeEntity.refreshCreatedAt() 참고), 삭제 후
    //     재등록된 품목만 새로 등록한 것처럼 맨 뒤로 이동하고, rename()은 createdAt을 전혀 건드리지
    //     않으니 단순 수정은 순서에 영향이 없음(한 번도 삭제/재등록된 적 없는 품목은 createdAt이
    //     최초 등록 순서 그대로라 기존 id 정렬과 결과가 동일함).
    private static final Sort LIST_SORT = Sort.by("createdAt").ascending();

    private final ItemService itemService;

    /**
     * IV-001 품목 목록 화면.
     * 등록/수정 모달의 폼 객체(itemCreateForm/itemUpdateForm)는 검증 실패로 이 화면에 다시
     * 돌아온 경우가 아니라면(=최초 진입) 여기서 빈 값으로 새로 만들어 넣음.
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, LIST_SORT);
        model.addAttribute("items", itemService.findAll(pageable));

        if (!model.containsAttribute("itemCreateForm")) {
            model.addAttribute("itemCreateForm", new ItemCreateForm());
        }
        if (!model.containsAttribute("itemUpdateForm")) {
            model.addAttribute("itemUpdateForm", new ItemUpdateForm());
        }
        return "inventory/item/list";
    }

    /**
     * REQ-F-S-009 품목 등록("새 품목 등록" 모달의 "등록" 버튼).
     *
     * 분기 처리:
     *   - 품목명 공백(@NotBlank 검증 실패)  → 목록 화면 유지 + 등록 모달을 연 채로 필드 오류 표시
     *   - 이미 등록된 품목명(중복)          → 목록 화면 유지 + 등록 모달을 연 채로 필드 오류 표시
     *   - 성공                           → redirect + 플래시 메시지 "품목이 등록되었습니다."
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
        model.addAttribute("items", itemService.findAll(PageRequest.of(0, PAGE_SIZE, LIST_SORT)));
        model.addAttribute("openRegisterModal", true);
        if (!model.containsAttribute("itemUpdateForm")) {
            model.addAttribute("itemUpdateForm", new ItemUpdateForm());
        }
        return "inventory/item/list";
    }

    /**
     * REQ-F-S-010 품목 정보 수정("품목 정보 수정" 모달의 "저장" 버튼).
     *
     * 분기 처리:
     *   - 품목명 공백 / 이미 등록된 다른 품목명(중복) → 목록 유지 + 해당 행의 수정 모달을 연 채로 오류 표시
     *   - 대상 품목 없음                              → redirect + 플래시 오류 메시지
     *   - 성공                                        → redirect + 플래시 메시지 "품목 정보가 수정되었습니다."
     */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int page,
                          @Valid @ModelAttribute("itemUpdateForm") ItemUpdateForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {

        if (!bindingResult.hasErrors()) {
            try {
                itemService.update(id, form.getName());
                redirectAttributes.addFlashAttribute("message", "품목 정보가 수정되었습니다.");
                return "redirect:/inventory?page=" + page;
            } catch (DuplicateItemNameException e) {
                bindingResult.rejectValue("name", "duplicate", e.getMessage());
            } catch (ItemNotFoundException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/inventory?page=" + page;
            }
        }

        // 실패 시: 방금 수정하려던 품목이 있던 그 페이지로 목록을 다시 채워서 같은 화면으로
        // 돌아가되, 해당 품목의 수정 모달이 열린 채로 보이도록 함(현재 재고/등록일시/수정일시
        // 재표시를 위해 items 목록을 그대로 재사용).
        model.addAttribute("items", itemService.findAll(PageRequest.of(page, PAGE_SIZE, LIST_SORT)));
        model.addAttribute("openEditItemId", id);
        if (!model.containsAttribute("itemCreateForm")) {
            model.addAttribute("itemCreateForm", new ItemCreateForm());
        }
        return "inventory/item/list";
    }

    /**
     * REQ-F-C-008 품목 삭제(목록 행의 "삭제" 버튼).
     * DepartmentController.delete()/PositionController.delete()와 동일한 구조: 성공/실패 모두
     * 목록 화면으로 리다이렉트하고, 실패 사유는 플래시 메시지로 전달.
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            itemService.delete(id);
            redirectAttributes.addFlashAttribute("message", "품목이 삭제(비활성화)되었습니다.");
        } catch (ItemInUseException | ItemNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/inventory";
    }
}
