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
 * REQ-NF-H-001 개선 완료: 화면(hr/position/list.html)은 사원 이름만 출력하는데도, 이 예외가
 * {@code List<Employee>}를 그대로 들고 있다가 컨트롤러가 그대로 플래시 속성(모델)에 실어 넘겨
 * {@code password}(해시) 등 민감 필드를 포함한 Employee 엔티티 전체가 응답(모델)에 실리고 있었다.
 * 생성자에서 즉시 {@link BlockingEmployeeView}(이름만 담은 DTO)로 변환해 저장하도록 고쳐서, 이
 * 예외를 통해서는 Employee 엔티티가 밖으로 전혀 나가지 않는다.
 */
public class PositionInUseException extends RuntimeException {

    private final Position position;
    private final List<BlockingEmployeeView> employees;

    public PositionInUseException(Position position, List<Employee> employees) {
        super("직책 '" + position.getPositionName() + "'을(를) 보유한 사원이 " + employees.size() + "명 있어 삭제할 수 없습니다.");
        this.position = position;
        this.employees = employees.stream()
                .map(BlockingEmployeeView::new)
                .collect(Collectors.toList());
    }

    public Position getPosition() {
        return position;
    }

    public List<BlockingEmployeeView> getEmployees() {
        return employees;
    }
}
