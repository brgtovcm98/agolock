package com.seu.seustock.model.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockMoveForm {

  @NotNull(message = "{valid.stockMove.sourceSpaceExternalId.notNull}")
  private UUID sourceSpaceExternalId;

  private UUID sourceShelfExternalId;

  private UUID sourceBoxExternalId;

  @NotNull(message = "{valid.stockMove.targetSpaceExternalId.notNull}")
  private UUID targetSpaceExternalId;

  private UUID targetShelfExternalId;

  private UUID targetBoxExternalId;

  @Valid
  @NotEmpty(message = "{valid.stockMove.items.notEmpty}")
  private List<Item> items = new ArrayList<>();

  private String memo;

  private boolean allView;

  private String keyword;

  private String sortBy;

  @Getter
  @Setter
  public static class Item {
    @NotNull(message = "{valid.stockMove.item.itemExternalId.notNull}")
    private UUID itemExternalId;

    @Min(value = 1, message = "{valid.stockMove.item.count.min}")
    private int count = 1;
  }
}
