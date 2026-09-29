package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Department;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * HR-004(부서 목록) 화면의 한 행을 표현하는 DTO.
 */
@Getter
public class DepartmentRowDto {
    private final Long id;
    private final String departmentName;
    private final boolean active;
    private final LocalDateTime createdAt;

    public DepartmentRowDto(Department department) {
        this.id = department.getId();
        this.departmentName = department.getDepartmentName();
        this.active = department.isActive();
        this.createdAt = department.getCreatedAt();
    }
}
