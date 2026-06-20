package com.seu.seustock.model.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class StockPanelDTO {
  private UUID itemExternalId;
  private String itemName;
  private UUID displayImageExternalId;
  private Integer count;
}
