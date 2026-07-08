package com.seu.seustock.model.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 홈 대시보드에 표시할 사용자 전체 재고 롤업 집계. 사용자 단위 단일 행으로, 모든 수량은 {@code IN_STOCK} 기준이며 상태별 카운트는 각 상태를 별도로 센다.
 *
 * <p>{@code targetTotalStock}은 사용자가 정한 <b>총 보유 수량 목표(소진/처리형)</b>이며 {@code null}이면 미설정이다. "처리해야 할
 * 물건"은 현재 총재고에서 목표를 뺀 초과분({@link #getToProcessCount()})으로 계산한다.
 */
@Getter
@Setter
@ToString
public class DashboardSummaryDTO {
  private int totalItemCount;
  private int totalStockCount;
  private BigDecimal totalValue;
  private int dispatchedCount;
  private int lostCount;
  private int damagedCount;
  private int disposedCount;
  private int keptCount;
  private int expiringCount;
  private int expiredCount;
  private int spaceCount;
  private Integer targetTotalStock;

  /** 목표가 설정되어 있는지 여부. */
  public boolean isTargetSet() {
    return targetTotalStock != null;
  }

  /** 처리해야 할 물건 수 = max(현재 총재고 - 목표, 0). 목표 미설정 시 0. */
  public int getToProcessCount() {
    if (targetTotalStock == null) {
      return 0;
    }
    return Math.max(totalStockCount - targetTotalStock, 0);
  }

  /** 목표 달성 여부(현재 총재고가 목표 이하). 목표 미설정 시 false. */
  public boolean isTargetAchieved() {
    return targetTotalStock != null && totalStockCount <= targetTotalStock;
  }

  /** 전체 재고 대비 초과분 비율(%). 프로그레스 바 렌더링에 사용. */
  public int getOverPercent() {
    if (totalStockCount <= 0) {
      return 0;
    }
    return (int) Math.round(getToProcessCount() * 100.0 / totalStockCount);
  }

  /** 주의가 필요한 파손 + 분실 합계. */
  public int getDamagedLostCount() {
    return damagedCount + lostCount;
  }

  /** 상태별 분포 막대의 분모. 보관중(kept)은 재고중의 부분집합이라 제외한다. */
  private int getStatusGrandTotal() {
    return totalStockCount + dispatchedCount + lostCount + damagedCount + disposedCount;
  }

  private int percentOf(int count) {
    int grandTotal = getStatusGrandTotal();
    if (grandTotal <= 0) {
      return 0;
    }
    return (int) Math.round(count * 100.0 / grandTotal);
  }

  /** 상태별 분포 막대에서 재고중 구간 비율(%). */
  public int getInStockPercent() {
    return percentOf(totalStockCount);
  }

  /** 상태별 분포 막대에서 출고 구간 비율(%). */
  public int getDispatchedPercent() {
    return percentOf(dispatchedCount);
  }

  /** 상태별 분포 막대에서 분실 구간 비율(%). */
  public int getLostPercent() {
    return percentOf(lostCount);
  }

  /** 상태별 분포 막대에서 파손 구간 비율(%). */
  public int getDamagedPercent() {
    return percentOf(damagedCount);
  }

  /** 상태별 분포 막대에서 폐기 구간 비율(%). */
  public int getDisposedPercent() {
    return percentOf(disposedCount);
  }
}
