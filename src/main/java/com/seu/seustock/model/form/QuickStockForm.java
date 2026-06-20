package com.seu.seustock.model.form;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class QuickStockForm {

  @NotBlank(message = "{valid.quickStock.name.notBlank}")
  @Size(max = 100, message = "{valid.quickStock.name.size}")
  private String name;

  @Size(max = 500, message = "{valid.quickStock.description.size}")
  private String description;

  @NotNull(message = "{valid.quickStock.spaceExternalId.notNull}")
  private UUID spaceExternalId;

  private UUID shelfExternalId;
  private UUID boxExternalId;

  @Min(value = 1, message = "{valid.quickStock.count.min}")
  @Max(value = 50, message = "{valid.quickStock.count.max}")
  private int count = 1;

  @PositiveOrZero(message = "{valid.price.positive}")
  @Digits(integer = 12, fraction = 0, message = "{valid.price.digits}")
  private BigDecimal price;

  private String memo;

  private MultipartFile imageFile;
  private String imageHash;
}
