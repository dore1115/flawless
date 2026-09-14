package com.dsa.flawless.controller;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.service.AttendanceService;
import com.dsa.flawless.service.EmployeeUserDetailsService;
import com.dsa.flawless.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Objects;


/**
 * [공용 파일] 메인 페이지 재고관리
 * 카드에 필요한 최소한(ItemService 주입 + itemSummary 모델 속성 1개)만 추가.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final ItemService itemService;
    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    @GetMapping("/")
    public String index(@SessionAttribute(name="SPRING_SECURITY_CONTEXT", required = false) SecurityContext securityContext, Model model) {
        if (securityContext != null) {
            Object principal = Objects.requireNonNull(securityContext.getAuthentication()).getPrincipal();
            if (principal instanceof EmployeeUserDetails userDetails) {
                String username = userDetails.getUsername();
                model.addAttribute("itemSummary", itemService.getDashboardSummary());
                model.addAttribute("attendanceSummary", attendanceService.getClockIn(username).getHour() + ":" + attendanceService.getClockIn(username).getMinute());
                model.addAttribute("employeeSummary", employeeService.getSumOfEmployee());
                return "/index";
            }
        }
        return "redirect:/login";
    }

}
