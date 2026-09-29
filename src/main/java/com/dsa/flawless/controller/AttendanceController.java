package com.dsa.flawless.controller;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.dto.AttendanceDto;
import com.dsa.flawless.service.AttendanceService;
import com.dsa.flawless.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@RequestMapping("/attendance")
@Controller
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final CalendarService calendarService;
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에, 어떤 사용자의 요청인지 Session에서 직접 Principal을 통해 꺼내서 판단한다.
    @GetMapping()
    public String mainPage(
            @SessionAttribute(name = "SPRING_SECURITY_CONTEXT", required = false) SecurityContext securityContext,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        Authentication authentication = securityContext != null ? securityContext.getAuthentication() : null;
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof EmployeeUserDetails userDetails) {
            String username = userDetails.getUsername();
            List<AttendanceDto> attendanceList = attendanceService.getList(username, year, month);
            Map<Integer, AttendanceDto> attendanceMap = attendanceList.stream()
                    .collect(Collectors.toMap(a -> a.getWorkDate().getDayOfMonth(), a -> a));

            model.addAttribute("attendanceMap", attendanceMap);
            model.addAttribute("calendar", calendarService.createCalendar(year, month));
            return "attendance/main";
        }
        return "redirect:/";
    }
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에, 어떤 사용자의 요청인지 Session에서 직접 Principal을 통해 꺼내서 판단한다.
    @PostMapping("/clockIn")
    public String clockIn(@SessionAttribute(name="SPRING_SECURITY_CONTEXT", required = false) SecurityContext securityContext, Model model) {
        Object principal = Objects.requireNonNull(securityContext.getAuthentication()).getPrincipal();
        if (principal instanceof EmployeeUserDetails userDetails) {
            String username = userDetails.getUsername();
            attendanceService.clockIn(username);
            return "redirect:/attendance";
        }

        return "index";
    }
    //! REQ-NF-G-003: 인가 아키텍처를 사용하지 않기에, 어떤 사용자의 요청인지 Session에서 직접 Principal을 통해 꺼내서 판단한다.
    @PostMapping("/clockOut")
    public String clockOut(@SessionAttribute(name="SPRING_SECURITY_CONTEXT", required = false) SecurityContext securityContext, Model model) {
        Object principal = Objects.requireNonNull(securityContext.getAuthentication()).getPrincipal();
        if (principal instanceof EmployeeUserDetails userDetails) {
            String username = userDetails.getUsername();
            attendanceService.clockOut(username);
            return "redirect:/attendance";
        }

        return "index";
    }
}
