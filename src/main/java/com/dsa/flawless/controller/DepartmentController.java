package com.dsa.flawless.controller;

import com.dsa.flawless.dto.DepartmentCreateForm;
import com.dsa.flawless.exception.DepartmentInUseException;
import com.dsa.flawless.service.DepartmentService;
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
 * HR-004(부서 목록, GET /hr/departments)를 처리한다.
 *
 * REQ-F-S-007(부서 신설): PositionController(HR-003, REQ-F-S-005)와 동일한 구조 —
 * 화면흐름도상 "생성" 버튼을 누르면 같은 화면(HR-004)에 머무르므로, 목록과 신설 폼을 한 화면에서
 * 함께 처리한다.
 *
 * REQ-F-S-008(부서 삭제): PositionController의 직책 삭제와 동일한 구조 — 목록의 각 행 "삭제" 버튼
 * (POST /hr/departments/{id}/delete)이 같은 화면으로 돌아온다. 소속 사원이 남아있어 삭제할 수 없는
 * 경우는 예외적 흐름이라 화면설계서 3-1 공통 예외 정책에 따라 GlobalExceptionHandler가 아닌
 * 컨트롤러 로컬에서 {@link DepartmentInUseException}을 잡아 원래 화면으로 리다이렉트 + 플래시
 * 메시지로 안내한다.
 * ※ REQ-NF-H-005 결함 시딩: 삭제 실행 권한(Role) 검증과 감사 로그 기록은 의도적으로 넣지 않았다
 * (누구나 삭제를 실행할 수 있고, 누가/언제 삭제했는지 기록이 남지 않는다) — 자세한 내용은
 * {@link DepartmentService}의 클래스 주석 참고.
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

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("message", "부서가 삭제(비활성화)되었습니다.");
        } catch (DepartmentInUseException e) {
            redirectAttributes.addFlashAttribute("deleteBlockedName", e.getDepartmentName());
            redirectAttributes.addFlashAttribute("blockingEmployees", e.getEmployees());
        }
        return "redirect:/hr/departments";
    }
}
