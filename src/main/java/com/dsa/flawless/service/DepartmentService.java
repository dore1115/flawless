package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.exception.DepartmentInUseException;
import com.dsa.flawless.repository.DepartmentRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-F-S-007(부서 신설)·REQ-F-S-008(부서 삭제)의 실제 처리를 담당하는 서비스 계층.
 *
 * 결함 시딩(성능_보안_결함_시딩_매트릭스, REQ-NF-H-002 - 입력값 서버단 검증 부재):
 * REQ-F-S-005(직책 신설)의 PositionService와 동일하게, create()는 의도적으로 부서명 중복 검사와
 * 필수값 검사를 하지 않는다. 화면(hr/department/list.html)의 <input required>는 브라우저에서만
 * 막을 뿐이라, 폼을 거치지 않고 API(POST /hr/departments)를 직접 두드리면 빈 문자열이나 중복된
 * 부서명도 그대로 저장을 시도하게 된다.
 *   - 빈 문자열("")은 department_name이 NOT NULL이라서 그대로 저장되어 버린다(빈 부서가 생김).
 *   - 중복된 부서명은 department 테이블의 UNIQUE 제약(uk_department_name)에 걸려 저장 자체는
 *     막히지만, 애플리케이션이 이를 미리 확인하지 않았기 때문에 DataIntegrityViolationException이
 *     그대로 터진다 — GlobalExceptionHandler가 없는 상태(REQ-NF-G-004)라 스택 트레이스가 담긴 500
 *     에러 페이지가 사용자에게 그대로 노출된다.
 * 개선 시 이 메서드 호출 전에 부서명 공백 여부와 중복 여부를 먼저 확인해 400으로 응답하도록 고친다.
 *
 * REQ-NF-H-005 개선(권한 부분, 2026-09-17): PositionService와 동일한 패턴 —
 * DepartmentController.delete()에 @PreAuthorize("hasRole('ADMIN_HR')")를 추가.
 * 감사 로그는 이번 범위에서 제외했다 — 담당자가 REQ-NF-G-005 작업 때 함께 해결할 예정.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * HR-004(부서 목록) 화면에 보여줄 목록. PositionService.findAll()과 동일한 결정(2026-09-02) —
     * 삭제(비활성화)된 부서는 더 이상 목록에 나타나지 않도록 {@code findAll()} 대신
     * {@code findByActiveTrue()}로 조회한다 — 신설한(사용 중인) 부서만 보이고, 삭제하면 목록에서
     * 바로 사라진다.
     */
    public List<Department> findAll() {
        return departmentRepository.findByActiveTrue();
    }

    @Transactional
    public void create(String departmentName) {
        departmentRepository.save(new Department(departmentName));
    }

    public Department getDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 부서가 없습니다."));
    }

    @Transactional
    public void delete(Long id) {
        Department department = getDepartmentOrThrow(id);
        List<Employee> members = employeeRepository.findByDepartment(department);
        if (!members.isEmpty()) {
            throw new DepartmentInUseException(department, members);
        }
        department.deactivate();
        departmentRepository.save(department);
    }
}
