package com.dsa.flawless.controller;

import com.dsa.flawless.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RequiredArgsConstructor
@RequestMapping("/attendance")
@Controller
public class AttendanceController {
    private final AttendanceService attendanceService;

    //! 의도적으로 PathVariable을 통해 ID를 받음으로써 다른 사람의 근태를 확인할 수 있는 결함
    @GetMapping("/{id}")
    public String mainPage(@PathVariable Long id, Model model) {


        return "/attendance/main";
    }
    //! 의도적으로 PathVariable을 통해 ID를 받음으로써 다른 사람의 출근 처리를 할 수 있는 결함
    @PostMapping("/{id}/clockIn")
    public String clockIn(@PathVariable Long id, Model model) {
        attendanceService.clockIn(id);
        return "redirect:/attendance";
    }
    //! 의도적으로 PathVariable을 통해 ID를 받음으로써 다른 사람의 퇴근 처리를 할 수 있는 결함
    @PostMapping("/{id}/clockOut")
    public String clockOut(@PathVariable Long id, Model model) {
        attendanceService.clockOut(id);
        return "redirect:/attendance";
    }
}
