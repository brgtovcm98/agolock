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
public class StockDetailDTO {
  private UUID externalId;
  private UUID itemExternalId;
  private String itemName;
  private UUID displayImageExternalId;
  private UUID spaceExternalId;
  private String spaceName;
  private UUID shelfExternalId;
  private String shelfName;
  private UUID boxExternalId;
  private String boxName;
  private UUID lotExternalId;
  private String serialNumber;
  private String lotNumber;
  private LocalDate expirationDate;
  private String memo;
  private StockStatus status;
  private boolean kept;
  private BigDecimal price;
  private BigDecimal itemPrice;
  private LocalDateTime createdAt;
}
