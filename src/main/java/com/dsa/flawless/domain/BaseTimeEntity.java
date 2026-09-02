package com.dsa.flawless.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

/**
 * 모든 엔티티가 공통으로 가지는 created_at(등록일시) / updated_at(수정일시)를
 * 자동으로 채워주는 상위(부모) 클래스.
 *
 * - ERD 문서의 "모든 테이블에 넣을 공통 컬럼" 규칙(created_at, updated_at)을
 *   테이블마다 반복해서 적지 않기 위해 만듦.
 * - @MappedSuperclass: 이 클래스 자체는 테이블이 되지 않고, 이 클래스를 상속하는
 *   자식 엔티티(Item, Stock, StockHistory 등)의 컬럼으로 합쳐짐.
 */

//! 여기 @Getter 없음
@MappedSuperclass
public abstract class BaseTimeEntity {
    
    // 최초 저장(INSERT)될 때 한 번만 값이 채워지고, 이후에는 수정되지 않음.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // save() 호출로 값이 바뀔 때마다(UPDATE) 자동으로 현재 시각으로 갱신.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // JPA 표준 생명주기 콜백: 이 엔티티가 처음 저장(INSERT)되기 "직전"에
    // 자동으로 호출됨. Hibernate가 실제 SQL INSERT를 날리기 전에 이 메서드를
    // 실행해서 createdAt/updatedAt에 현재 시각을 채워 넣음.
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    // JPA 표준 생명주기 콜백: 이 엔티티의 변경사항이 저장(UPDATE)되기 "직전"에
    // 자동으로 호출됨. updatedAt만 현재 시각으로 다시 채움
    // (createdAt은 최초 저장 시각을 그대로 유지해야 하므로 여기서는 건드리지 않음).
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}