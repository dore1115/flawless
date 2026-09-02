package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 품목(item) 마스터 엔티티.
 * ERD 정의: 재고 입출고 대상이 되는 품목 정보. "실제 재고 수량은 두지 않는다"는 것이
 * 이 테이블 설계의 핵심 규칙(수량은 Stock 엔티티가 별도로 관리).
 *
 * 품목 등록/수정/삭제 기능(REQ-F-S-009~010, REQ-F-C-008)은 이번 작업 범위가 아니므로,
 * 여기서는 "입고 처리가 참조만 하는" 최소한의 형태로만 만듦.
 */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor // JPA는 엔티티를 리플렉션으로 생성하기 위해 기본 생성자가 반드시 필요.
public class Item extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT와 매칭
    private Long id;

    // ERD: name VARCHAR(100), UNIQUE, NOT NULL - 중복 등록 불가
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    // ERD: is_deleted BOOLEAN, 기본값 false - 논리 삭제(soft delete) 플래그.
    // true인 품목은 입고 화면의 선택 목록에서 제외.
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public Item(String name) {
        this.name = name;
    }

    // Lombok @Getter가 boolean 필드는 isDeleted()가 아니라 getDeleted()를 만들 수도 있어
    // (필드명이 이미 'deleted'라 실제로는 isDeleted()가 생성되지만) 명시적으로 한 번 더 선언해
    // 어디서 호출해도 헷갈리지 않도록 함.
    public boolean isDeleted() {
        return deleted;
    }
}
