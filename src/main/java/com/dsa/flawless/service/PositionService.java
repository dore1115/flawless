package com.dsa.flawless.service;

import com.dsa.flawless.domain.Employee;
import com.dsa.flawless.domain.Position;
import com.dsa.flawless.exception.PositionInUseException;
import com.dsa.flawless.repository.EmployeeRepository;
import com.dsa.flawless.repository.PositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-F-S-005(직책 신설)·REQ-F-S-006(직책 삭제)의 실제 처리를 담당하는 서비스 계층.
 *
 * 결함 시딩(성능_보안_결함_시딩_매트릭스, REQ-NF-H-002 - 입력값 서버단 검증 부재):
 * create()는 의도적으로 직책명 중복 검사와 필수값 검사를 하지 않는다. 화면(hr/position/list.html)의
 * <input required>는 브라우저에서만 막을 뿐이라, 폼을 거치지 않고 API(POST /hr/positions)를 직접
 * 두드리면 빈 문자열이나 중복된 직책명도 그대로 저장을 시도하게 된다.
 *   - 빈 문자열("")은 position_name이 NOT NULL이라서 그대로 저장되어 버린다(빈 직책이 생김).
 *   - 중복된 직책명은 position 테이블의 UNIQUE 제약(uk_position_name)에 걸려 저장 자체는 막히지만,
 *     애플리케이션이 이를 미리 확인하지 않았기 때문에 DataIntegrityViolationException이 그대로
 *     터진다 — GlobalExceptionHandler가 없는 상태(REQ-NF-G-004)라 스택 트레이스가 담긴 500 에러
 *     페이지가 사용자에게 그대로 노출된다.
 * 개선 시 이 메서드 호출 전에 직책명 공백 여부와 중복 여부를 먼저 확인해 400으로 응답하도록 고친다.
 *
 * 결함 시딩(성능_보안_결함_시딩_매트릭스, REQ-NF-H-005 - 직책/부서 삭제 권한·감사로그 결함):
 * delete()는 의도적으로 (1) 호출자의 권한(Role)을 확인하지 않고 — REQ-NF-G-003(전역 인가 아키텍처
 * 부재)과 동일하게, 로그인 여부와 무관하게 누구나 삭제를 실행할 수 있다 — (2) 누가/언제 삭제했는지
 * 남기는 감사 로그도 기록하지 않는다. "소속 사원이 있으면 삭제를 막는다"는 업무 규칙만 지킨다.
 * 개선 시 컨트롤러 진입 전 인가 체크를 추가하고, 삭제 처리 성공 시 처리자·시각·변경 전후 값을
 * position_history 테이블에 남기도록 고친다.
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
        positionRepository.save(new Position(positionName));
    }

    public Position getPositionOrThrow(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 직책이 없습니다."));
    }

    @Transactional
    public void delete(Long id) {
        Position position = getPositionOrThrow(id);
        List<Employee> holders = employeeRepository.findByPosition(position);
        if (!holders.isEmpty()) {
            throw new PositionInUseException(position, holders);
        }
        position.deactivate();
        positionRepository.save(position);
    }
}
