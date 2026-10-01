package com.dsa.flawless.service;

import com.dsa.flawless.domain.Department;
import com.dsa.flawless.domain.DepartmentHistory;
import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.dto.DepartmentRowDto;
import com.dsa.flawless.exception.DepartmentInUseException;
import com.dsa.flawless.repository.DepartmentHistoryRepository;
import com.dsa.flawless.repository.DepartmentRepository;
import com.dsa.flawless.repository.EmployeeRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-F-S-007(부서 신설)·REQ-F-S-008(부서 삭제)의 실제 처리를 담당하는 서비스 계층.
 *
 * REQ-NF-H-002 개선 완료: PositionService.create()와 동일한 패턴 — 저장 전에 부서명 공백 여부와
 * 중복 여부를 확인해 400(Bad Request)으로 응답한다. 중복 검사는
 * {@link DepartmentRepository#existsByDepartmentNameAndActiveTrue}로 active(사용 중)인 부서만
 * 대상으로 하므로, 삭제(비활성화)된 이름은 중복으로 치지 않고 재사용(재신설)할 수 있다 —
 * 담당자 요청(2026-09-21)에 따른 결정.
 *
 * REQ-NF-H-005 개선 완료 — 삭제 Authority 미비 + 감사로그 부재:
 * PositionService와 완전히 동일한 패턴 —
 * (1) DepartmentController.delete()에 @PreAuthorize("hasRole('ADMIN_HR')")를 추가(2026-09-17).
 * (2) delete()가 부서 비활성화에 성공한 뒤 {@link DepartmentHistory#ofDelete}로 처리자/시각/
 *     변경 전 값을 department_history 테이블에 append한다(2026-09-22). 자세한 설계 이유는
 *     {@link PositionService} 클래스 주석 참고.
 *
 * REQ-NF-H-009 개선 완료 — 동시성 문제(Lost Update):
 * PositionService.delete()와 동일한 패턴 — {@link com.dsa.flawless.domain.Department#version}에
 * @Version 필드를 추가하고, save() 대신 saveAndFlush()를 써서 메서드 안에서 즉시 버전 충돌을
 * 확인하도록 했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentHistoryRepository departmentHistoryRepository;

    /**
     * HR-004(부서 목록) 화면에 보여줄 목록. PositionService.findAll()과 동일한 결정(2026-09-02) —
     * 삭제(비활성화)된 부서는 더 이상 목록에 나타나지 않도록 {@code findAll()} 대신
     * {@code findByActiveTrue()}로 조회한다 — 신설한(사용 중인) 부서만 보이고, 삭제하면 목록에서
     * 바로 사라진다.
     */
    public List<DepartmentRowDto> findAll() {
        return departmentRepository.findByActiveTrue().stream()
                .map(DepartmentRowDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void create(String departmentName) {
        if (!StringUtils.hasText(departmentName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "부서명을 입력해 주세요.");
        }
        if (departmentRepository.existsByDepartmentNameAndActiveTrue(departmentName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 부서명입니다.");
        }
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
        try {
            departmentRepository.saveAndFlush(department);
        } catch (OptimisticLockingFailureException e) {
            // REQ-NF-H-009 개선: 다른 요청이 이 부서를 먼저 수정·커밋해 버전이 바뀐 상태.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "다른 요청이 먼저 이 부서를 처리했습니다. 목록을 새로고침한 뒤 다시 시도해 주세요.");
        }
        // REQ-NF-H-005 개선(감사 로그): 삭제가 실제로 성공한 뒤에만 이력을 남긴다.
        departmentHistoryRepository.save(DepartmentHistory.ofDelete(department, currentEmployeeId()));
    }

    // REQ-NF-H-005 개선(감사 로그): 현재 로그인한 사용자의 employee id를 얻는다. getReferenceByUsername()은
    // EntityManager.getReference() 기반이라 별도 SELECT 없이 id가 채워진 프록시만 돌려준다.
    private Long currentEmployeeId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.getReferenceByUsername(username).getId();
    }
}
