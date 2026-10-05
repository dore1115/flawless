package com.dsa.flawless.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AttendanceDto {
    LocalDate workDate;
    LocalDateTime clockIn;
    LocalDateTime clockOut;

    public AttendanceDto(LocalDate workDate, LocalDateTime clockIn, LocalDateTime clockOut) {
        this.workDate = workDate;
        this.clockIn = clockIn;
        this.clockOut = clockOut;
    }
}