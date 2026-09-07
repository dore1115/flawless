package com.dsa.flawless.controller;

import com.dsa.flawless.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/attendance")
@Controller
public class AttendanceController {
    private final LocalTime CHECK_IN_LIMIT = LocalTime.of(14, 0, 0);
    private final AttendanceService attendanceService;

    //! 의도적으로 PathVariable을 통해 ID를 받음으로써 다른 사람의 출근 시간을 찍을 수 있게 함
    @PostMapping("/{id}/clockIn")
    public String clockIn(@PathVariable Long id, Model model) {
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isAfter(CHECK_IN_LIMIT)) {
            model.addAttribute("error", "14시 이후로는 출근 처리 불가");
            return "/attendance/attendance";
        }
        attendanceService.clockIn(id);
        return "redirect:/attendance";
    }
}
