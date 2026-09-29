package com.dsa.flawless.controller;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.dto.AttendanceDto;
import com.dsa.flawless.service.AttendanceService;
import com.dsa.flawless.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@RequestMapping("/attendance")
@Controller
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final CalendarService calendarService;
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에(anyRequest().permitAll()), 로그인하지 않은 사용자도
    //! 이 경로에 접근할 수 있다. @AuthenticationPrincipal은 그런 경우 null로 주입되므로 이를 확인해 처리한다.
    @GetMapping()
    public String mainPage(
            @AuthenticationPrincipal EmployeeUserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        if (userDetails == null) {
            return "redirect:/";
        }

        List<AttendanceDto> attendanceList = attendanceService.getList(userDetails.getUsername(), year, month);
        Map<Integer, AttendanceDto> attendanceMap = attendanceList.stream()
                .collect(Collectors.toMap(a -> a.getWorkDate().getDayOfMonth(), a -> a));

        model.addAttribute("attendanceMap", attendanceMap);
        model.addAttribute("calendar", calendarService.createCalendar(year, month));
        return "attendance/main";
    }
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에(anyRequest().permitAll()), 로그인하지 않은 사용자도
    //! 이 경로에 접근할 수 있다. @AuthenticationPrincipal은 그런 경우 null로 주입되므로 이를 확인해 처리한다.
    @PostMapping("/clockIn")
    public String clockIn(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/";
        }
        attendanceService.clockIn(userDetails.getUsername());
        return "redirect:/attendance";
    }
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에(anyRequest().permitAll()), 로그인하지 않은 사용자도
    //! 이 경로에 접근할 수 있다. @AuthenticationPrincipal은 그런 경우 null로 주입되므로 이를 확인해 처리한다.
    @PostMapping("/clockOut")
    public String clockOut(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/";
        }
        attendanceService.clockOut(userDetails.getUsername());
        return "redirect:/attendance";
    }
}
