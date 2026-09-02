package com.dsa.flawless.service;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import java.util.List;

/**
 * REQ-F-S-006(직책 삭제): 삭제하려는 직책을 보유한 사원이 남아있어 삭제를 진행할 수 없을 때 던진다.
 * 컨트롤러({@link com.dsa.flawless.controller.PositionController})가 이를 잡아 원래 화면
 * (HR-003)으로 리다이렉트하며 대상 직책명과 보유 사원 목록을 플래시 속성으로 전달한다
 * (화면설계서 3-1 공통 예외 정책 - 비즈니스 예외는 리다이렉트 + 플래시 메시지로 처리).
 */
public class PositionInUseException extends RuntimeException {

    private final Position position;
    private final List<Employee> employees;

    public PositionInUseException(Position position, List<Employee> employees) {
        super("직책 '" + position.getPositionName() + "'을(를) 보유한 사원이 " + employees.size() + "명 있어 삭제할 수 없습니다.");
        this.position = position;
        this.employees = employees;
    }

    public Position getPosition() {
        return position;
    }

    public List<Employee> getEmployees() {
        return employees;
    }
}
