package com.dsa.flawless.controller;

import com.dsa.flawless.dto.DepartmentCreateForm;
import com.dsa.flawless.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * HR-004(부서 목록, GET /hr/departments)를 처리한다.
 *
 * REQ-F-S-007(부서 신설): PositionController(HR-003, REQ-F-S-005)와 동일한 구조 —
 * 화면흐름도상 "생성" 버튼을 누르면 같은 화면(HR-004)에 머무르므로, 목록과 신설 폼을 한 화면에서
 * 함께 처리한다. 삭제(REQ-F-S-008)는 이번 범위 밖이라 다루지 않는다.
 *
 * 비즈니스 로직은 {@link DepartmentService}에 있다 — 컨트롤러는 Repository를 직접 호출하지 않는다.
 */
@Controller
@RequestMapping("/hr/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        if (!model.containsAttribute("departmentForm")) {
            model.addAttribute("departmentForm", new DepartmentCreateForm());
        }
        return "hr/department/list";
    }

    @PostMapping
    public String create(@ModelAttribute DepartmentCreateForm form, RedirectAttributes redirectAttributes) {
        departmentService.create(form.getDepartmentName());

        redirectAttributes.addFlashAttribute("message", "부서가 등록되었습니다.");
        return "redirect:/hr/departments";
    }
}
