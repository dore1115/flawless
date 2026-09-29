package com.dsa.flawless.exception;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.dto.BlockingEmployeeView;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REQ-F-S-006(직책 삭제): 삭제하려는 직책을 보유한 사원이 남아있어 삭제를 진행할 수 없을 때 던진다.
 * 컨트롤러({@link com.dsa.flawless.controller.PositionController})가 이를 잡아 원래 화면
 * (HR-003)으로 리다이렉트하며 대상 직책명과 보유 사원 목록을 플래시 속성으로 전달한다
 * (화면설계서 3-1 공통 예외 정책 - 비즈니스 예외는 리다이렉트 + 플래시 메시지로 처리).
 *
 * 컨트롤러가 Position/Employee 엔티티를 직접 다루지 않도록, 생성자에서 바로 직책명과
 * {@link BlockingEmployeeView} 목록으로 변환해 들고 있는다(REQ-NF-H-001 소속 사원 목록 Entity
 * 직접 응답 결함 해결).
 */
public class PositionInUseException extends RuntimeException {

    private final String positionName;
    private final List<BlockingEmployeeView> employees;

    public PositionInUseException(Position position, List<Employee> employees) {
        super("직책 '" + position.getPositionName() + "'을(를) 보유한 사원이 " + employees.size() + "명 있어 삭제할 수 없습니다.");
        this.positionName = position.getPositionName();
        this.employees = employees.stream().map(BlockingEmployeeView::new).collect(Collectors.toList());
    }

    public String getPositionName() {
        return positionName;
    }

    public List<BlockingEmployeeView> getEmployees() {
        return employees;
    }
}
