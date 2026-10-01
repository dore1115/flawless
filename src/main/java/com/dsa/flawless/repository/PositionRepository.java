package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Position;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByActiveTrue();

    // REQ-NF-H-002 개선: PositionService.create()가 저장 전에 중복 직책명 여부를 먼저 확인할 때 쓴다.
    // active(is_active=1)인 직책만 대상으로 하므로, 삭제(비활성화)된 이름은 중복으로 치지 않고
    // 재사용(재신설)할 수 있다.
    boolean existsByPositionNameAndActiveTrue(String positionName);
}
