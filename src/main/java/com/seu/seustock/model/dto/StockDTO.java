package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.StockStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class StockDTO {
  private Long id;
  private UUID externalId;
  private Long itemId;
  private Long spaceId;
  private Long shelfId;
  private Long boxId;
  private Long lotId;
  private String serialNumber;
  private String lotNumber;
  private LocalDate expirationDate;
  private String memo;
  private StockStatus status;
  private boolean kept;
  private BigDecimal price;
  private LocalDateTime createdAt;
}
