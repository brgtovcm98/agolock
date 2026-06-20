package com.seu.seustock.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImageDTO {
  private Long id;
  private UUID externalId;
  private Long userId;
  private String storagePath;
  private String originalFilename;
  private String contentType;
  private Long sizeBytes;
  private String contentHash;
  private LocalDateTime createdAt;
}
