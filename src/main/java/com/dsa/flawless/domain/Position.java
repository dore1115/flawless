package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 직책 마스터 테이블 (테이블_목록: position / 役職, 담당 김도영).
 * 삭제(REQ-F-S-006)는 물리 삭제 대신 is_active를 0으로 전환하는 soft delete로 구현한다.
 *
 * 이번 작업(REQ-F-M-004)에서는 신설/삭제 기능은 다루지 않고,
 * 사원 배치 변경 화면(HR-002)에서 선택 가능한 "활성 직책 목록"을 제공하는 용도로만 쓰인다.
 */
@Entity
@Table(name = "position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position_name", nullable = false, unique = true, length = 50)
    private String positionName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // REQ-NF-H-009 개선 완료: 동시성 문제(Lost Update).
    // delete()(비활성화)가 낙관적 락 없이 곧바로 save()를 호출한다 — @Version을 추가해, 거의 동시에
    // 들어온 두 요청이 같은 직책 행을 각자 읽어 처리하는 사이 한쪽이 먼저 커밋되면 나머지 한쪽의
    // save()가 OptimisticLockingFailureException을 던지도록 했다. 자세한 동작은
    // {@link com.dsa.flawless.domain.Employee#version} 주석 참고.
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public Position(String positionName) {
        this.positionName = positionName;
        this.active = true;
    }

    /**
     * REQ-F-S-006(직책 삭제): 물리 삭제 대신 is_active를 0으로 전환한다(soft delete).
     * 소속 사원이 남아있는지 확인하는 책임은 서비스 계층({@link com.dsa.flawless.service.PositionService})에
     * 있다 — 이 메서드 자체는 무조건 비활성화만 수행한다.
     */
    public void deactivate() {
        this.active = false;
    }
}
