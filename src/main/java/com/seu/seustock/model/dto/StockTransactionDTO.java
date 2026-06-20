package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.TransactionType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class StockTransactionDTO {
  private Long id;
  private UUID externalId;
  private Long stockId;
  private TransactionType transactionType;
  private Long fromSpaceId;
  private Long fromShelfId;
  private Long fromBoxId;
  private Long toSpaceId;
  private Long toShelfId;
  private Long toBoxId;
  private String memo;
  private LocalDateTime createdAt;
}
