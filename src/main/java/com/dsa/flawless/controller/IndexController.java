package com.dsa.flawless.controller;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.service.AttendanceService;
import com.dsa.flawless.service.EmployeeService;
import com.dsa.flawless.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


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
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에(anyRequest().permitAll()), 로그인하지 않은 사용자도
    //! 이 경로에 접근할 수 있다. @AuthenticationPrincipal은 그런 경우 null로 주입되므로 이를 확인해 처리한다.
    @GetMapping("/")
    public String index(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("itemSummary", itemService.getDashboardSummary());
        model.addAttribute("attendanceSummary", attendanceService.getClockIn(userDetails.getUsername()));
        model.addAttribute("employeeSummary", employeeService.getSumOfEmployee());
        return "index";
    }

}
