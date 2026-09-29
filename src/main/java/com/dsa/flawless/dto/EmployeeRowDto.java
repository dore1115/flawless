package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Role;
import lombok.Getter;

/**
 * HR-001(사원 목록) 화면의 한 행을 표현하는 DTO.
 */
@Getter
public class EmployeeRowDto {
    private final Long id;
    private final String name;
    private final String departmentName;
    private final String positionName;
    private final Role role;

    public EmployeeRowDto(Employee employee) {
        this.id = employee.getId();
        this.name = employee.getName();
        this.departmentName = employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : "-";
        this.positionName = employee.getPosition() != null ? employee.getPosition().getPositionName() : "-";
        this.role = employee.getRole();
    }
}
