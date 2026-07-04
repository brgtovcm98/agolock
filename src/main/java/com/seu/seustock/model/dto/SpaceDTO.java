package com.seu.seustock.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SpaceDTO {
  private Long id;
  private UUID externalId;
  private Long userId;
  private String name;
  private UUID imageExternalId;
  private LocalDateTime createdAt;
}
