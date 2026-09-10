package com.dsa.flawless.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
public class CalendarDto {
    private final int year;
    private final int month;
    private final int firstDay;
    private final int lastDay;
    private final List<List<Integer>> grid;
    private final int prevYear;
    private final int prevMonth;
    private final int nextYear;
    private final int nextMonth;
}
