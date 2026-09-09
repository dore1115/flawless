package com.dsa.flawless.dto;

import lombok.Getter;

/**
 * 메인 페이지(index.html) 재고관리 카드에 필요한 요약 정보.
 * 요구사항 문서에 메인/대시보드 화면 자체가 정의돼 있지 않아, 카드에 원래 있던 하드코딩 값
 * ("128 품목", "재고 부족 알림 3건")을 실제 데이터로 바꿔달라는 요청에 맞춰 최소한으로 추가함.
 * "재고 부족"의 기준은 문서에 없어서 가장 단순한 정의(활성 품목 중 현재 재고 수량이 0인 것
 * = 품절)로 잡음(사용자 확인 완료).
 */
@Getter
public class ItemDashboardSummaryDto {
    private final long activeItemCount;
    private final long lowStockCount;

    public ItemDashboardSummaryDto(long activeItemCount, long lowStockCount) {
        this.activeItemCount = activeItemCount;
        this.lowStockCount = lowStockCount;
    }
}
