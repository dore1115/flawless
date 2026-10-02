package com.dsa.flawless.controller;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Role;
import com.dsa.flawless.service.EmployeeRegisterService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


// REQ-F-S-004 사원 계정 등록. ROLE_ADMIN_HR만 접근 가능.
// ⚠️ Employee가 Builder 전용(setter 없음)이라 자동 바인딩이 안 되고,
//    DTO도 의도적으로 안 쓰기로 했으므로 개별 @RequestParam으로 값을 받습니다.

// REQ-NF-M-001 개선: 아래 @PreAuthorize는 SecurityConfig에 @EnableMethodSecurity가 없어서 현재 무시된다.
// @EnableMethodSecurity를 켜면 다른 기능(REQ-NF-H-004/H-005)의 결함까지 함께 바뀌므로,
// 이 컨트롤러 안에서 checkAdminHr()로 직접 권한을 검사한다. (@PreAuthorize는 추후 전역 인가 적용 대비로 유지)
@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_HR')")
public class EmployeeRegisterController {
	
	private final EmployeeRegisterService employeeRegisterService;
	
	// REQ-NF-M-001: 로그인한 사용자가 인사관리자(ROLE_ADMIN_HR)인지 서버에서 직접 확인
	private void checkAdminHr() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean isAdminHr = auth != null && auth.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMIN_HR".equals(a.getAuthority()));
		if (!isAdminHr) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인사관리자만 접근할 수 있습니다.");
		}
	}
	
	// GET /admin/members/register : 사원등록 폼
	@GetMapping("/register")
	public String registerForm(Model model) {
		checkAdminHr();
		model.addAttribute("roleOptions", Role.values());
		return "register/register";
	}
	
	// POST /admin/members : 등록 처리
	@PostMapping
	public String register(@RequestParam String username,
	                       @RequestParam String password,
	                       @RequestParam String name,
	                       @RequestParam String email,
	                       @RequestParam String phone,
	                       @RequestParam(required = false) String address,
	                       @RequestParam Long departmentId,
	                       @RequestParam Long positionId,
	                       @RequestParam String role,
	                       Model model,
	                       HttpSession session) {
		checkAdminHr();
		try {
			Employee saved = employeeRegisterService.registerEmployee(
					username, password, name, email, phone, address, departmentId, positionId, role);
			
			// AU-002b: 임시 비밀번호를 완료 화면에서 1회만 보여주기 위해 세션에 잠깐 담아둠
			session.setAttribute("justRegisteredLoginId", saved.getUsername());
			session.setAttribute("justRegisteredTempPassword", password);
			
			return "redirect:/admin/members/complete";
		} catch (IllegalStateException e) {
			// DTO가 없어서 실패 시 입력값 재표시는 하지 않습니다 (관리자가 다시 입력)
			model.addAttribute("roleOptions", Role.values());
			model.addAttribute("errorMessage", e.getMessage()); // 아이디/이메일 중복, 잘못된 권한값
			return "register/register";
		}
	}
	
	// GET /admin/members/complete : AU-002b 임시 비밀번호 1회 노출 화면
	@GetMapping("/complete")
	public String complete(Model model, HttpSession session) {
		checkAdminHr();
		Object loginId = session.getAttribute("justRegisteredLoginId");
		Object tempPassword = session.getAttribute("justRegisteredTempPassword");
		
		if (loginId == null || tempPassword == null) {
			// 새로고침/직접 접근으로 재조회하는 경우 -> 등록 폼으로 돌려보냄 (재노출 방지)
			return "redirect:/admin/members/register";
		}
		
		model.addAttribute("loginId", loginId);
		model.addAttribute("tempPassword", tempPassword);
		
		// 1회 노출 후 즉시 세션에서 제거 -> 새로고침하면 더 이상 안 보임
		session.removeAttribute("justRegisteredLoginId");
		session.removeAttribute("justRegisteredTempPassword");
		
		return "register/complete";
	}
}