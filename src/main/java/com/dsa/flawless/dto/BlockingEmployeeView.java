package com.dsa.flawless.dto;

import com.dsa.flawless.domain.Employee;
import lombok.Getter;

/**
 * 직책/부서 삭제가 차단됐을 때(REQ-F-S-006/REQ-F-S-008) 보유 중인 사원을 화면에 보여주기 위한
 * 최소 정보 DTO. {@link com.dsa.flawless.exception.PositionInUseException}/
 * {@link com.dsa.flawless.exception.DepartmentInUseException}이 Employee 엔티티를 그대로
 * 컨트롤러 밖으로 들고 나가지 않도록, 이름만 담아 전달한다.
 */
@Getter
public class BlockingEmployeeView {
    private final String name;

    public BlockingEmployeeView(Employee employee) {
        this.name = employee.getName();
    }
}
