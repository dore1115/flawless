package com.dsa.flawless.exception;

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

    //! REQ-NF-H-001 소속 사원 목록 Entity 직접 응답
    //! H-001을 고치는 핵심은 한 줄로 말하면, "삭제 차단 시 넘기는 사원 목록을 Employee 엔티티가 아니라 이름만 담은 DTO로 바꿔치기" 입니다.
    //! 이름만 담은 작은 DTO 하나 새로 만듭니다 — dto/BlockingEmployeeView.java (필드는 name 하나).
    //! PositionInUseException/DepartmentInUseException이 지금 List<Employee>를 그대로 들고 있는데,
    //! 생성자 안에서 List<BlockingEmployeeView>로 변환해서 저장하도록 고칩니다.
    //! 이 지점 하나만 막으면 이 예외를 통해서는 다시는 Employee가 밖으로 안 나갑니다.
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
