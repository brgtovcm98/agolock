package com.seu.seustock.model.form;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockUpdateForm {

  @Size(max = 255, message = "{valid.stockUpdate.serialNumber.size}")
  private String serialNumber;

  @Size(max = 255, message = "{valid.stockUpdate.lotNumber.size}")
  private String lotNumber;

  private LocalDate expirationDate;

  @PositiveOrZero(message = "{valid.price.positive}")
  @Digits(integer = 12, fraction = 0, message = "{valid.price.digits}")
  private BigDecimal price;

  private String memo;
}
