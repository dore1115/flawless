package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HR-002 화면(사원 정보 조회·수정)의 폼 바인딩 객체.
 *
 * REQ-F-M-004("사원 1명에게 할당된 정보를 다른 정보로 변경한다")는 직책·부서뿐 아니라
 * 이름/이메일/연락처/주소까지 사원에게 할당된 정보 전체를 가리킨다. 예전에 별도로 있던
 * 개인정보 수정 요구사항(구 REQ-F-C-005/006)이 최신 요구사항정의서에서 완전히 삭제되고
 * 다른 기능 번호로 재사용된 것도 이 항목들이 REQ-F-M-004 하나로 흡수됐다는 근거다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAssignmentForm {

    private String name;
    private String email;
    private String phone;
    private String address;
    private Long positionId;
    private Long departmentId;

    public static EmployeeAssignmentForm from(Employee employee) {
        Long positionId = (employee.getPosition() != null) ? employee.getPosition().getId() : null;
        Long departmentId = (employee.getDepartment() != null) ? employee.getDepartment().getId() : null;
        return new EmployeeAssignmentForm(
                employee.getName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getAddress(),
                positionId,
                departmentId
        );
    }
}
