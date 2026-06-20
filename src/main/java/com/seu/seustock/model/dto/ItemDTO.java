package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.TrackingMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemDTO {
  private Long id;
  private UUID externalId;
  private Long userId;
  private String name;
  private String description;
  private BigDecimal price;
  private TrackingMode serialMode = TrackingMode.NONE;
  private String serialPrefix;
  private int serialPaddingLength;
  private int serialIncrementUnit = 1;
  private long serialNextSequence;
  private TrackingMode lotMode = TrackingMode.NONE;
  private String lotVendorCode;
  private String lotDateFormat = "yyyyMMdd";
  private boolean lotIncludeSequence = true;
  private String lotSequenceKey;
  private int lotNextSequence;
  private Integer expirationPeriodDays;
  private boolean active;
  private UUID primaryImageExternalId;
  private LocalDateTime createdAt;
  private int stockCount;
  private int spaceCount;
  private int historyCount;
}
