package com.dsa.flawless.service;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.exception.PositionInUseException;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.repository.PositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-F-S-005(직책 신설)·REQ-F-S-006(직책 삭제)의 실제 처리를 담당하는 서비스 계층.
 *
 * REQ-NF-H-002 개선 완료: create()가 저장 전에 (1) 직책명 공백 여부, (2) 중복 여부를 확인해 둘 중
 * 하나라도 걸리면 400(Bad Request)으로 응답한다. {@link EmployeeAssignmentService#validatePersonalInfo}
 * 와 동일하게 {@code StringUtils.hasText()} + {@code ResponseStatusException(BAD_REQUEST, ...)}
 * 패턴을 그대로 따랐다 — 공백 문자(" ")만 입력해도 걸러지고(hasText는 trim 기준으로 판단),
 * 중복 검사는 {@link PositionRepository#existsByPositionNameAndActiveTrue}로 저장 전에 미리
 * 확인하므로 DB의 UNIQUE 제약(uk_position_name)에 기대어 DataIntegrityViolationException이 터지는
 * 일이 없다. active(사용 중)인 직책만 검사 대상이라, 삭제(비활성화)된 이름은 중복으로 치지 않고
 * 재사용(재신설)할 수 있다 — 담당자 요청(2026-09-21)에 따른 결정.
 * (참고: 이 프로젝트엔 아직 GlobalExceptionHandler가 없어서(REQ-NF-G-004) ResponseStatusException도
 * 화면에는 Whitelabel 에러 페이지로 나간다 — EmployeeController 쪽 검증과 동일한 한계.)
 *
 * REQ-NF-H-005 개선(권한 부분, 2026-09-17): PositionController.delete()에
 * @PreAuthorize("hasRole('ADMIN_HR')")를 추가해 인사관리자만 삭제를 실행할 수 있다
 * (REQ-NF-H-004에서 이미 켜진 @EnableMethodSecurity를 그대로 사용).
 * 감사 로그(누가/언제 삭제했는지 기록)는 이번 범위에서 제외했다 — 담당자가 REQ-NF-G-005 작업 때
 * 함께 해결할 예정이라, position_history 테이블 저장 로직은 그때 추가한다.
 *
 * REQ-NF-H-009 개선 완료 — 동시성 문제(Lost Update):
 * delete()가 낙관적 락 없이 곧바로 save()를 호출해, 같은 직책에 대한 두 삭제 요청이 거의 동시에
 * 들어오면 나중에 flush되는 쪽이 예외 없이 조용히 성공(사실상 무해하지만, 향후 직책명 변경 등
 * 다른 필드를 다루는 기능이 추가되면 진짜 Lost Update로 번질 수 있는 상태)해버릴 수 있었다.
 * {@link com.dsa.flawless.domain.Position#version}에 @Version 필드를 추가하고, save() 대신
 * saveAndFlush()를 써서 메서드 안에서 즉시 버전 충돌을 확인하도록 했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * HR-003(직책 목록) 화면에 보여줄 목록. 담당자 요청(2026-09-02)에 따라 삭제(비활성화)된 직책은
     * 더 이상 목록에 나타나지 않도록 {@code findAll()} 대신 {@code findByActiveTrue()}로 조회한다 —
     * 신설한(사용 중인) 직책만 보이고, 삭제하면 목록에서 바로 사라진다.
     */
    public List<Position> findAll() {
        return positionRepository.findByActiveTrue();
    }

    @Transactional
    public void create(String positionName) {
        if (!StringUtils.hasText(positionName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직책명을 입력해 주세요.");
        }
        if (positionRepository.existsByPositionNameAndActiveTrue(positionName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 직책명입니다.");
        }
        positionRepository.save(new Position(positionName));
    }

    public Position getPositionOrThrow(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 직책이 없습니다."));
    }

    // REQ-NF-H-005 감사로그 부재는 REQ-NF-G-005 작업 때 함께 해결 예정 — 여기서는 권한 체크만
    // 컨트롤러 쪽(@PreAuthorize)에 추가하고, delete() 본문은 그대로 둔다.
    @Transactional
    public void delete(Long id) {
        Position position = getPositionOrThrow(id);
        List<Employee> holders = employeeRepository.findByPosition(position);
        if (!holders.isEmpty()) {
            throw new PositionInUseException(position, holders);
        }
        position.deactivate();
        try {
            positionRepository.saveAndFlush(position);
        } catch (OptimisticLockingFailureException e) {
            // REQ-NF-H-009 개선: 다른 요청이 이 직책을 먼저 수정·커밋해 버전이 바뀐 상태.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "다른 요청이 먼저 이 직책을 처리했습니다. 목록을 새로고침한 뒤 다시 시도해 주세요.");
        }
    }
}
