package com.seu.seustock.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Read-only aggregate of a space's contents, shown as a summary strip on the space list. Keyed by
 * {@code spaceExternalId}. All stock-based counts consider only {@code IN_STOCK} units except
 * {@code damagedLostCount}, which counts {@code DAMAGED} + {@code LOST}.
 */
@Getter
@Setter
@ToString
public class SpaceSummaryDTO {
  private UUID spaceExternalId;
  private int itemCount;
  private int stockCount;
  private BigDecimal totalValue;
  private int expiringCount;
  private int expiredCount;
  private int damagedLostCount;
  private int shelfCount;
  private int boxCount;
  private LocalDateTime lastActivityAt;
}
