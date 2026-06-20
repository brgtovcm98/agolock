package com.seu.seustock.model.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemSpaceStockDTO {
  private UUID spaceExternalId;
  private String spaceName;
  private int count;
}
