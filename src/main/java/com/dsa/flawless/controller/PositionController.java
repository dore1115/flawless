package com.dsa.flawless.controller;

import com.dsa.flawless.dto.PositionCreateForm;
import com.dsa.flawless.exception.PositionInUseException;
import com.dsa.flawless.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * HR-003(직책 목록, GET /hr/positions)을 처리한다.
 *
 * REQ-F-S-005(직책 신설): 화면설계서 1.화면 흐름도상 "생성" 버튼을 누르면 같은 화면(HR-003)에
 * 머무르므로, 목록과 신설 폼을 한 화면에서 함께 처리한다.
 *
 * REQ-F-S-006(직책 삭제): 목록의 각 행 "삭제" 버튼(POST /hr/positions/{id}/delete)이 같은 화면으로
 * 돌아온다. 소속 사원이 남아있어 삭제할 수 없는 경우는 예외적 흐름이라 화면설계서 3-1 공통 예외
 * 정책에 따라 GlobalExceptionHandler가 아닌 컨트롤러 로컬에서 {@link PositionInUseException}을
 * 잡아 원래 화면으로 리다이렉트 + 플래시 메시지로 안내한다.
 * ※ REQ-NF-H-005 결함 시딩: 삭제 실행 권한(Role) 검증과 감사 로그 기록은 의도적으로 넣지 않았다
 * (누구나 삭제를 실행할 수 있고, 누가/언제 삭제했는지 기록이 남지 않는다) — 자세한 내용은
 * {@link PositionService}의 클래스 주석 참고.
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

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            positionService.delete(id);
            redirectAttributes.addFlashAttribute("message", "직책이 삭제(비활성화)되었습니다.");
        } catch (PositionInUseException e) {
            redirectAttributes.addFlashAttribute("deleteBlockedName", e.getPositionName());
            redirectAttributes.addFlashAttribute("blockingEmployees", e.getEmployees());
        }
        return "redirect:/hr/positions";
    }
}
