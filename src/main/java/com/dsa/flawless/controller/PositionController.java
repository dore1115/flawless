package com.dsa.flawless.controller;

import com.dsa.flawless.dto.PositionCreateForm;
import com.dsa.flawless.exception.PositionInUseException;
import com.dsa.flawless.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REQ-NF-H-005 개선(권한 부분): {@link #delete(Long, RedirectAttributes)}에
 * {@code @PreAuthorize("hasRole('ADMIN_HR')")}를 추가해 인사관리자만 직책을 삭제할 수 있도록
 * 제한했다({@link com.dsa.flawless.config.SecurityConfig}의 {@code @EnableMethodSecurity}가
 * REQ-NF-H-004 작업에서 이미 켜져 있어 별도 설정 변경 없이 바로 적용됨). 목록 조회·신설은 이번
 * 범위에 포함하지 않았다 — H-005는 "삭제" 권한 미비를 지적한 항목이라 그대로 뒀다.
 * 감사 로그(누가/언제 삭제했는지 기록)는 이번 변경에 포함하지 않았다 — 담당자가 REQ-NF-G-005
 * 작업 때 함께 해결할 예정이라 그때 position_history 저장 로직을 추가한다.
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

    // REQ-NF-H-005 개선(권한 부분): 인사관리자(ADMIN_HR)만 직책을 삭제할 수 있도록 제한.
    // ADMIN_HR이 아닌 계정이 호출하면 403으로 차단된다.
    @PreAuthorize("hasRole('ADMIN_HR')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            positionService.delete(id);
            redirectAttributes.addFlashAttribute("message", "직책이 삭제(비활성화)되었습니다.");
        } catch (PositionInUseException e) {
            redirectAttributes.addFlashAttribute("deleteBlockedName", e.getPosition().getPositionName());
            redirectAttributes.addFlashAttribute("blockingEmployees", e.getEmployees());
        }
        return "redirect:/hr/positions";
    }
}
