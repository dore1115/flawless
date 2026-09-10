package com.dsa.flawless.service;

import com.dsa.flawless.dto.CalendarDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CalendarService {
    private final Clock clock;

    public CalendarDto createCalendar(Integer year, Integer month) {
        // 1. 기준 날짜 설정
        LocalDate now = LocalDate.now(clock);
        int currentYear = (year != null) ? year : now.getYear();
        int currentMonth = (month != null) ? month : now.getMonthValue();

        LocalDate firstDayOfMonth = LocalDate.of(currentYear, currentMonth, 1);
        int lastDay = firstDayOfMonth.lengthOfMonth();
        int startDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue() % 7; // 일요일(0) ~ 토요일(6)

        // 2. 달력 그리드 데이터 생성
        List<List<Integer>> calendarGrid = new ArrayList<>();
        List<Integer> currentWeek = new ArrayList<>();

        // 첫 주의 시작 전 빈 칸
        for (int i = 0; i < startDayOfWeek; i++) {
            currentWeek.add(null);
        }

        // 날짜 채우기
        for (int day = 1; day <= lastDay; day++) {
            currentWeek.add(day);
            if (currentWeek.size() == 7) {
                calendarGrid.add(calendarGrid.size(), currentWeek);
                currentWeek = new ArrayList<>();
            }
        }

        // 마지막 주의 남은 빈 칸
        if (!currentWeek.isEmpty()) {
            while (currentWeek.size() < 7) {
                currentWeek.add(null);
            }
            calendarGrid.add(currentWeek);
        }

        // 3. 이전달, 다음달 계산
        LocalDate prevMonth = firstDayOfMonth.minusMonths(1);
        LocalDate nextMonth = firstDayOfMonth.plusMonths(1);

        // DTO에 담아서 반환
        return new CalendarDto(
                currentYear, currentMonth, firstDayOfMonth.getDayOfMonth(), lastDay, calendarGrid,
                prevMonth.getYear(), prevMonth.getMonthValue(),
                nextMonth.getYear(), nextMonth.getMonthValue());
    }

}
