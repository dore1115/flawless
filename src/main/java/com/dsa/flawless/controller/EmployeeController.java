package com.dsa.flawless.controller;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.dto.EmployeeAssignmentForm;
import com.dsa.flawless.service.EmployeeAssignmentService;
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
 * HR-001(사원 목록, GET /hr/employees)과
 * HR-002(사원 정보 조회·수정, GET /hr/employees/{id}/info)를 처리한다.
 *
 * REQ-F-M-004("사원 1명에게 할당된 정보를 다른 정보로 변경한다")는 직책·부서뿐 아니라
 * 이름/이메일/연락처/주소까지 사원에게 할당된 정보 전체를 대상으로 한다. 그래서 이 컨트롤러는
 * 폼으로 받은 여섯 개 필드를 모두 서비스에 그대로 넘긴다.
 *
 * 비즈니스 로직/유효성 검증은 {@link EmployeeAssignmentService}에 있다 — 컨트롤러는
 * Repository를 직접 호출하지 않고 서비스 계층만 거친다(ArchUnit으로 계층을 강제할 계획이므로).
 *
 * REQ-NF-H-004(직책/부서 단일 변경 Authority 미비, IDOR) 개선: 이 컨트롤러 전체를
 * {@code hasRole('ADMIN_HR')}로 제한한다 — 기획서상 "인사정보 수정은 인사관리자만 가능"이므로
 * 목록 조회부터 정보 변경까지 전부 인사관리자 전용으로 묶는 게 맞다.
 * {@link com.dsa.flawless.config.SecurityConfig}에 {@code @EnableMethodSecurity}가 선행돼 있어야
 * 이 어노테이션이 실제로 동작한다.
 */
@Controller
@RequestMapping("/hr/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_HR')")
public class EmployeeController {

    private final EmployeeAssignmentService employeeAssignmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("employees", employeeAssignmentService.findAll());
        return "hr/employee/list";
    }

    @GetMapping("/{id}/info")
    public String info(@PathVariable Long id, Model model) {
        Employee employee = employeeAssignmentService.getEmployeeOrThrow(id);

        model.addAttribute("employee", employee);
        model.addAttribute("positionOptions", employeeAssignmentService.getActivePositions());
        model.addAttribute("departmentOptions", employeeAssignmentService.getActiveDepartments());
        if (!model.containsAttribute("assignmentForm")) {
            model.addAttribute("assignmentForm", EmployeeAssignmentForm.from(employee));
        }
        return "hr/employee/info";
    }
    
    // REQ-NF-H-004 개선 완료: 클래스 레벨 @PreAuthorize("hasRole('ADMIN_HR')")가 이 메서드에도
    // 그대로 적용된다 — ADMIN_HR이 아닌 계정이 id를 바꿔가며 호출하면 403으로 차단된다.

    @PostMapping("/{id}/assignment")
    public String updateAssignment(@PathVariable Long id,
                                    @ModelAttribute EmployeeAssignmentForm form,
                                    RedirectAttributes redirectAttributes) {
        employeeAssignmentService.updateEmployeeInfo(id, form.getName(), form.getEmail(), form.getPhone(),
                form.getAddress(), form.getPositionId(), form.getDepartmentId());

        redirectAttributes.addFlashAttribute("message", "변경이 완료되었습니다.");
        return "redirect:/hr/employees/" + id + "/info";
    }
}
