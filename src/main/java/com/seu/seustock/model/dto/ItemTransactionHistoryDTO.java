package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TransactionType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemTransactionHistoryDTO {
  private UUID transactionExternalId;
  private TransactionType transactionType;
  private String memo;
  private LocalDateTime createdAt;

  private UUID stockExternalId;
  private String serialNumber;
  private String lotNumber;
  private StockStatus status;

  private String spaceName;
  private String shelfName;
  private String boxName;

  private String fromSpaceName;
  private String fromShelfName;
  private String fromBoxName;
  private String toSpaceName;
  private String toShelfName;
  private String toBoxName;
}
