package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Employee;
import lombok.Getter;

/**
 * REQ-NF-H-001 개선: 직책/부서 삭제가 차단됐을 때(HR-003/HR-004 화면) "이 직책/부서를 아직 보유한
 * 사원 목록"을 화면에 보여주기 위한 뷰 전용 DTO.
 *
 * {@link com.dsa.flawless.exception.PositionInUseException}/
 * {@link com.dsa.flawless.exception.DepartmentInUseException}이 기존에는 {@code List<Employee>}를
 * 그대로 들고 있다가 컨트롤러가 이를 그대로 {@code RedirectAttributes} 플래시 속성(모델)에 실어
 * 화면으로 넘겼다 — 화면 템플릿(hr/position/list.html, hr/department/list.html)은
 * {@code employee.name}만 출력하지만, 모델에는 {@code password}(해시)를 포함한 Employee 엔티티
 * 전체가 실려 있었다. 이 DTO는 화면에 실제로 필요한 이름 하나만 담아, 그 외 민감 필드(비밀번호 해시,
 * 이메일, 전화번호, 주소 등)는 애초에 모델에 올라가지 않도록 한다.
 */
@Getter
public class BlockingEmployeeView {

    private final String name;

    public BlockingEmployeeView(Employee employee) {
        this.name = employee.getName();
    }
}
