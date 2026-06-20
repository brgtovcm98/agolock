package com.seu.seustock.model.form;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockInOutForm {

  @NotNull(message = "{valid.stockInOut.itemExternalId.notNull}")
  private UUID itemExternalId;

  @NotNull(message = "{valid.stockInOut.spaceExternalId.notNull}")
  private UUID spaceExternalId;

  private UUID shelfExternalId;

  private UUID boxExternalId;

  @Min(value = 1, message = "{valid.stockInOut.count.min}")
  @Max(value = 50, message = "{valid.stockInOut.count.max}")
  private int count = 1;

  private String serialNumbersText;

  private String lotNumber;

  private java.time.LocalDate expirationDate;

  @PositiveOrZero(message = "{valid.price.positive}")
  @Digits(integer = 12, fraction = 0, message = "{valid.price.digits}")
  private BigDecimal price;

  private String memo;

  private boolean includeKept = false;
}
