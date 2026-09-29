package com.dsa.flawless.controller;

import com.dsa.flawless.dto.EmployeeAssignmentForm;
import com.dsa.flawless.service.EmployeeAssignmentService;
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
 * HR-001(사원 목록, GET /hr/employees)과
 * HR-002(사원 정보 조회·수정, GET /hr/employees/{id}/info)를 처리한다.
 *
 * REQ-F-M-004("사원 1명에게 할당된 정보를 다른 정보로 변경한다")는 직책·부서뿐 아니라
 * 이름/이메일/연락처/주소까지 사원에게 할당된 정보 전체를 대상으로 한다. 그래서 이 컨트롤러는
 * 폼으로 받은 여섯 개 필드를 모두 서비스에 그대로 넘긴다.
 *
 * 비즈니스 로직/유효성 검증은 {@link EmployeeAssignmentService}에 있다 — 컨트롤러는
 * Repository를 직접 호출하지 않고 서비스 계층만 거친다(ArchUnit으로 계층을 강제할 계획이므로).
 */
@Controller
@RequestMapping("/hr/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeAssignmentService employeeAssignmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("employees", employeeAssignmentService.findAll());
        return "hr/employee/list";
    }

    @GetMapping("/{id}/info")
    public String info(@PathVariable Long id, Model model) {
        model.addAttribute("employeeId", id);
        model.addAttribute("positionOptions", employeeAssignmentService.getActivePositions());
        model.addAttribute("departmentOptions", employeeAssignmentService.getActiveDepartments());
        if (!model.containsAttribute("assignmentForm")) {
            model.addAttribute("assignmentForm", employeeAssignmentService.getAssignmentFormOrThrow(id));
        }
        return "hr/employee/info";
    }
    
    // TODO(REQ-NF-M-001 IDOR / 권한 상승 방지 부재): 이 엔드포인트는 ROLE_ADMIN_HR 전용이어야
    // 하는데 @PreAuthorize가 없고, SecurityConfig도 anyRequest().authenticated()만 걸어놔서
    // 로그인만 했으면 어떤 role이든 id를 바꿔가며 임의 사원 정보를 수정할 수 있다(IDOR).
    // -> @PreAuthorize("hasRole('ADMIN_HR')") 추가 (SecurityConfig에 @EnableMethodSecurity 선행 필요)
    
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
