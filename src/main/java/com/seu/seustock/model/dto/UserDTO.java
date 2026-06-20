package com.seu.seustock.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserDTO {
  private Long id;
  private UUID externalId;
  private String email;
  private String nickname;
  @ToString.Exclude private String password;
  private LocalDateTime createdAt;
}
