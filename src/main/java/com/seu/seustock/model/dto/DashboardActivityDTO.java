package com.seu.seustock.model.dto;

import com.seu.seustock.model.enumeration.TransactionType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 홈 대시보드의 최근 활동 위젯에 표시할 한 건의 재고 트랜잭션. 품목명을 포함해 사용자 전체 범위에서 조회된다. */
@Getter
@Setter
@ToString
public class DashboardActivityDTO {
  private TransactionType transactionType;
  private String memo;
  private LocalDateTime createdAt;
  private String itemName;
  private String spaceName;
}
