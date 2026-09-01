package com.dsa.flawless.controller;

import com.dsa.flawless.dto.PositionCreateForm;
import com.dsa.flawless.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * HR-003(직책 목록, GET /hr/positions)을 처리한다.
 *
 * REQ-F-S-005(직책 신설): 화면설계서 1.화면 흐름도상 "생성" 버튼을 누르면 같은 화면(HR-003)에
 * 머무르므로, 목록과 신설 폼을 한 화면에서 함께 처리한다. 삭제(REQ-F-S-006)는 이번 범위 밖이라
 * 다루지 않는다.
 *
 * 비즈니스 로직은 {@link PositionService}에 있다 — 컨트롤러는 Repository를 직접 호출하지 않는다.
 */
@Controller
@RequestMapping("/hr/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("positions", positionService.findAll());
        if (!model.containsAttribute("positionForm")) {
            model.addAttribute("positionForm", new PositionCreateForm());
        }
        return "hr/position/list";
    }

    @PostMapping
    public String create(@ModelAttribute PositionCreateForm form, RedirectAttributes redirectAttributes) {
        positionService.create(form.getPositionName());

        redirectAttributes.addFlashAttribute("message", "직책이 등록되었습니다.");
        return "redirect:/hr/positions";
    }
}
