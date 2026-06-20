package com.seu.seustock.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemLotDTO {
  private Long id;
  private UUID externalId;
  private Long itemId;
  private String itemName;
  private String lotNumber;
  private LocalDate expirationDate;
  private LocalDateTime createdAt;
}
