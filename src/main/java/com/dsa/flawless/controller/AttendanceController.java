package com.dsa.flawless.controller;

import com.dsa.flawless.config.EmployeeUserDetails;
import com.dsa.flawless.domain.Attendance;
import com.dsa.flawless.dto.AttendanceDto;
import com.dsa.flawless.exception.BusinessException;
import com.dsa.flawless.service.AttendanceService;
import com.dsa.flawless.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
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

    // 출근/퇴근은 화면에서 fetch로 호출하고 응답 본문을 그대로 alert로 띄우므로,
    // GlobalExceptionHandler의 error.html 대신 ErrorCode 메시지만 텍스트로 돌려준다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handleBusinessException(BusinessException e) {
        log.warn("[{}] attendance - code={}", e.getClass().getSimpleName(), e.getErrorCode().name());
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getErrorCode().getMessage());
    }
}
