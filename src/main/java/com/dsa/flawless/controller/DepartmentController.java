package com.dsa.flawless.controller;

import com.dsa.flawless.dto.DepartmentCreateForm;
import com.dsa.flawless.exception.DepartmentInUseException;
import com.dsa.flawless.service.DepartmentService;
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
 * REQ-NF-H-005 개선(권한 부분): {@link #delete(Long, RedirectAttributes)}에
 * {@code @PreAuthorize("hasRole('ADMIN_HR')")}를 추가해 인사관리자만 부서를 삭제할 수 있도록
 * 제한했다(PositionController와 동일한 패턴, {@code @EnableMethodSecurity}는 REQ-NF-H-004에서
 * 이미 활성화됨). 목록 조회는 이번 범위에 포함하지 않았다.
 * 신설(create) 권한 보완(2026-09-22, H-005와 별개): 요구사항정의서 #10(부서 신설)의 권한 칸이
 * "인사관리자"로 명시돼 있음에도 {@link #create(DepartmentCreateForm, RedirectAttributes)}에는
 * 권한 체크가 없어 EMPLOYEE 계정도 부서를 새로 만들 수 있었다. delete()와 동일한
 * {@code @PreAuthorize("hasRole('ADMIN_HR')")}를 추가해 막았다 — PositionController와 동일한 이유로
 * 결함 시딩 매트릭스의 어느 REQ-NF-H 항목에도 속하지 않는다.
 * REQ-NF-H-005 개선(감사 로그, 2026-09-22): 삭제 성공 시 {@link DepartmentService#delete(Long)}가
 * {@code department_history}에 이력(요청자/시각/삭제 전 이름)을 append한다 — 자세한 내용은
 * {@link DepartmentService}의 클래스 주석 참고. 컨트롤러 코드 자체는 변경 없음(서비스 내부 구현).
 * REQ-NF-H-001 개선: {@link DepartmentInUseException#getEmployees()}가 이제
 * {@code List<BlockingEmployeeView>}(이름만 담은 DTO)를 반환하므로, 아래 {@code blockingEmployees}
 * 플래시 속성에는 더 이상 Employee 엔티티(비밀번호 해시 등 포함)가 실리지 않는다. 컨트롤러 코드 자체는
 * 변경 없음 — 예외 쪽에서 이미 변환된 값을 그대로 전달만 한다.
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

    // 요구사항정의서 #10(부서 신설)의 권한 칸도 "인사관리자"로 명시돼 있는데 create()에는 권한
    // 체크가 빠져 있었다(PositionController와 동일한 누락 — H-005는 "삭제" Authority만 지적한
    // 항목이라 create()는 다루지 않았음). delete()에 이미 쓰던 것과 동일한 @PreAuthorize를 그대로
    // 적용해 EMPLOYEE 계정이 부서를 새로 만들 수 있던 문제를 막는다(2026-09-22).
    @PreAuthorize("hasRole('ADMIN_HR')")
    @PostMapping
    public String create(@ModelAttribute DepartmentCreateForm form, RedirectAttributes redirectAttributes) {
        departmentService.create(form.getDepartmentName());

        redirectAttributes.addFlashAttribute("message", "부서가 등록되었습니다.");
        return "redirect:/hr/departments";
    }

    // REQ-NF-H-005 개선(권한 부분): 인사관리자(ADMIN_HR)만 부서를 삭제할 수 있도록 제한.
    // ADMIN_HR이 아닌 계정이 호출하면 403으로 차단된다.
    @PreAuthorize("hasRole('ADMIN_HR')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("message", "부서가 삭제(비활성화)되었습니다.");
        } catch (DepartmentInUseException e) {
            redirectAttributes.addFlashAttribute("deleteBlockedName", e.getDepartment().getDepartmentName());
            redirectAttributes.addFlashAttribute("blockingEmployees", e.getEmployees());
        }
        return "redirect:/hr/departments";
    }
}
