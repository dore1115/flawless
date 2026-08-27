package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서 마스터 테이블 (테이블_목록: department / 部署, 담당 김도영).
 * 삭제(REQ-F-S-008)는 물리 삭제 대신 is_active를 0으로 전환하는 soft delete로 구현한다.
 *
 * 이번 작업(REQ-F-M-004)에서는 신설/삭제 기능은 다루지 않고,
 * 사원 배치 변경 화면(HR-002)에서 선택 가능한 "활성 부서 목록"을 제공하는 용도로만 쓰인다.
 */
@Entity
@Table(name = "department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_name", nullable = false, unique = true, length = 50)
    private String departmentName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Department(String departmentName) {
        this.departmentName = departmentName;
        this.active = true;
    }
}
