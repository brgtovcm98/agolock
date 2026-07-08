package com.seu.seustock.model.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 홈 대시보드의 공간별 요약 위젯에 표시할 한 공간의 행. {@link SpaceSummaryDTO}에 공간명을 더한 축약형이다. */
@Getter
@Setter
@ToString
public class DashboardSpaceRowDTO {
  private UUID spaceExternalId;
  private String name;
  private int stockCount;
  private int expiringCount;
  private int expiredCount;
  private int damagedLostCount;

  public boolean isNeedsAttention() {
    return expiringCount + expiredCount + damagedLostCount > 0;
  }
}
