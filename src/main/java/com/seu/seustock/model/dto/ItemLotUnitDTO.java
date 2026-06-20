package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.StockStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemLotUnitDTO {
  private UUID stockExternalId;
  private String serialNumber;
  private StockStatus status;
  private boolean kept;
  private LocalDateTime createdAt;
  private String spaceName;
  private String shelfName;
  private String boxName;
}
