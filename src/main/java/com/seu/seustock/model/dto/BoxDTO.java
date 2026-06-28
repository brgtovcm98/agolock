package com.seu.seustock.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class BoxDTO {
  private Long id;
  private UUID externalId;
  private Long shelfId;
  private String name;
  private LocalDateTime createdAt;
  private int stockCount;
}
