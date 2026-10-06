package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.repository.DepartmentRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.repository.PositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-F-M-004(사원 정보 단일 변경)의 실제 처리를 담당하는 서비스 계층.
 *
 * REQ-F-M-004는 "사원 1명에게 할당된 정보를 다른 정보로 변경한다"는 요구사항 텍스트 그대로,
 * 직책/부서뿐 아니라 이름/이메일/연락처/주소까지 사원에게 할당된 정보 전체를 대상으로 한다.
 * 예전에 별도로 있던 개인정보 수정 요구사항(구 REQ-F-C-005/006)이 최신 요구사항정의서에서
 * 완전히 삭제되고 다른 기능 번호로 재사용된 것도 이 항목들이 REQ-F-M-004 하나로 흡수됐다는 근거다.
 *
 * Controller가 Repository를 직접 호출하지 않도록 분리했다 — 이 프로젝트는 기술스택에
 * ArchUnit을 두어 "계층간 알아서는 안되는 정보가 오가는 것"을 막겠다고 명시했으므로,
 * Controller → Service → Repository 흐름을 지키는 편이 이후 ArchUnit 규칙 도입 시에도 안전하다.
 *
 * 결함 시딩(성능_보안_결함_시딩_매트릭스, F1/REQ-NF-H-004 - IDOR):
 * updateEmployeeInfo()는 호출자(로그인한 인사관리자인지)를 검증하지 않는다. 로그인 기능 자체가
 * 아직 없어 지금은 인증조차 없는 상태이며, 사원 id만 알면 누구든 임의 사원의 정보를 바꿀 수 있다.
 * 개선 시 이 메서드 호출 전에 인사관리자 인증/인가를 확인하는 코드를 추가한다.
 */
@Service
@RequiredArgsConstructor
public class EmployeeAssignmentService {

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Employee getEmployeeOrThrow(Long employeeId) {
        // REQ-F-M-004 비고: 대상 사원이 없으면 '대상 없음'(404) 오류
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 사원이 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Position> getActivePositions() {
        return positionRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Department> getActiveDepartments() {
        return departmentRepository.findByActiveTrue();
    }

    @Transactional
    public void updateEmployeeInfo(Long employeeId, String name, String email, String phone, String address,
                                    Long positionId, Long departmentId) {
        Employee employee = getEmployeeOrThrow(employeeId);
        validatePersonalInfo(name, email, phone, address);
        Position position = findActivePositionOrBadRequest(positionId);
        Department department = findActiveDepartmentOrBadRequest(departmentId);

        // 결함 시딩(F7/REQ-NF-H-010, Lost Update): 낙관적/비관적 락 없이 그대로 갱신한다.
        employee.updatePersonalInfo(name, email, phone, address);
        employee.changeAssignment(position, department);
        employeeRepository.save(employee);
    }

    private void validatePersonalInfo(String name, String email, String phone, String address) {
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름을 입력해 주세요.");
        }
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일을 입력해 주세요.");
        }
        if (!StringUtils.hasText(phone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연락처를 입력해 주세요.");
        }
        if (!StringUtils.hasText(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주소를 입력해 주세요.");
        }
    }

    private Position findActivePositionOrBadRequest(Long positionId) {
        if (positionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직책을 선택해 주세요.");
        }
        return positionRepository.findById(positionId)
                .filter(Position::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 직책입니다."));
    }

    private Department findActiveDepartmentOrBadRequest(Long departmentId) {
        if (departmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "부서를 선택해 주세요.");
        }
        return departmentRepository.findById(departmentId)
                .filter(Department::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 부서입니다."));
    }
}
