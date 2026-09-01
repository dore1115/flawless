package com.dsa.flawless.service;

import com.dsa.flawless.domain.Position;
import com.dsa.flawless.repository.PositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-F-S-005(직책 신설)의 실제 처리를 담당하는 서비스 계층.
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;
    
    public List<Position> findAll() {
        return positionRepository.findAll();
    }

    @Transactional
    public void create(String positionName) {
        positionRepository.save(new Position(positionName));
    }
}
