package com.seu.seustock.model.form;

import com.seu.seustock.model.enumeration.TrackingMode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ItemForm {

  @NotBlank(message = "{valid.item.name.notBlank}")
  @Size(max = 100, message = "{valid.item.name.size}")
  private String name;

  @Size(max = 500, message = "{valid.item.description.size}")
  private String description;

  @PositiveOrZero(message = "{valid.price.positive}")
  @Digits(integer = 12, fraction = 0, message = "{valid.price.digits}")
  private BigDecimal price;

  @NotNull(message = "{valid.item.serialMode.notNull}")
  private TrackingMode serialMode = TrackingMode.NONE;

  @Size(max = 100, message = "{valid.item.serialPrefix.size}")
  private String serialPrefix;

  @Min(value = 0, message = "{valid.item.serialPaddingLength.min}")
  @Max(value = 16, message = "{valid.item.serialPaddingLength.max}")
  private int serialPaddingLength = 0;

  @Min(value = 1, message = "{valid.item.serialIncrementUnit.min}")
  private int serialIncrementUnit = 1;

  @Min(value = 0, message = "{valid.item.serialNextSequence.min}")
  private long serialNextSequence = 0;

  @NotNull(message = "{valid.item.lotMode.notNull}")
  private TrackingMode lotMode = TrackingMode.NONE;

  @Size(max = 100, message = "{valid.item.lotVendorCode.size}")
  private String lotVendorCode;

  @Size(max = 32, message = "{valid.item.lotDateFormat.size}")
  private String lotDateFormat = "yyyyMMdd";

  private boolean lotIncludeSequence = true;

  @Min(value = 0, message = "{valid.item.lotNextSequence.min}")
  private int lotNextSequence = 0;

  @Min(value = 1, message = "{valid.item.expirationPeriodDays.min}")
  private Integer expirationPeriodDays;

  private MultipartFile imageFile;
  private String imageHash;
}
