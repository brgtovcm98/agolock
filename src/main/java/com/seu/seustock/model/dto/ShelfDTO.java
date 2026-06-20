package com.seu.seustock.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ShelfDTO {
  private Long id;
  private UUID externalId;
  private Long spaceId;
  private String name;
  private LocalDateTime createdAt;
}
