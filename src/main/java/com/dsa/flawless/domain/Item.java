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
 * 품목 등록(REQ-F-S-009)/수정(REQ-F-S-010)/삭제(REQ-F-C-008) 전부 이 클래스 범위.
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

    /**
     * REQ-F-S-010(품목 정보 수정): 품목명을 바꿈. 중복 검사는(등록 시와 동일한 규칙) 여기가 아니라
     * ItemService가 저장 전에 미리 확인함(Stock.increase()/decrease()와 같은 패턴 -
     * "바꿔도 되는지"는 서비스 책임, "실제로 바꾸는 방법"만 엔티티 책임).
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * REQ-F-C-008(품목 삭제): 물리 삭제가 아니라 is_deleted를 true로 바꾸는 논리 삭제.
     * "재고 수량이 0이 아니면 삭제 불가" 규칙 확인은 Position/Department.deactivate()와
     * 동일하게 서비스 계층 책임 - 이 메서드는 무조건 비활성화만 함.
     */
    public void delete() {
        this.deleted = true;
    }

    /**
     * 품목 재활성화(요구사항 문서에는 명시돼 있지 않지만 필요해서 추가함).
     * existsByName() 중복 검사가 deleted 여부를 가리지 않아서(ItemRepository 참고),
     * 재활성화 경로가 없으면 한 번 삭제한 품목명은 영구히 재사용이 막히게 됨 - 그걸 풀어주는 용도.
     */
    public void activate() {
        this.deleted = false;
    }

    // Lombok @Getter가 boolean 필드는 isDeleted()가 아니라 getDeleted()를 만들 수도 있어
    // (필드명이 이미 'deleted'라 실제로는 isDeleted()가 생성되지만) 명시적으로 한 번 더 선언해
    // 어디서 호출해도 헷갈리지 않도록 함.
    public boolean isDeleted() {
        return deleted;
    }
}
