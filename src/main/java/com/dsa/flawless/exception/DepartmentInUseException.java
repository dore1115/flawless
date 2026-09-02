package com.dsa.flawless.exception;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import java.util.List;

/**
 * REQ-F-S-008(부서 삭제): 삭제하려는 부서를 보유한 사원이 남아있어 삭제를 진행할 수 없을 때 던진다.
 * 컨트롤러({@link com.dsa.flawless.controller.DepartmentController})가 이를 잡아 원래 화면
 * (HR-004)으로 리다이렉트하며 대상 부서명과 소속 사원 목록을 플래시 속성으로 전달한다
 * (화면설계서 3-1 공통 예외 정책 - 비즈니스 예외는 리다이렉트 + 플래시 메시지로 처리).
 */
public class DepartmentInUseException extends RuntimeException {

    private final Department department;
    private final List<Employee> employees;

    public DepartmentInUseException(Department department, List<Employee> employees) {
        super("부서 '" + department.getDepartmentName() + "'을(를) 보유한 사원이 " + employees.size() + "명 있어 삭제할 수 없습니다.");
        this.department = department;
        this.employees = employees;
    }

    public Department getDepartment() {
        return department;
    }

    public List<Employee> getEmployees() {
        return employees;
    }
}
