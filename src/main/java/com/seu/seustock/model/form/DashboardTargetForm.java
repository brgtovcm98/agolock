package com.seu.seustock.model.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 홈 대시보드의 총 보유 수량 목표 설정 폼. */
@Getter
@Setter
public class DashboardTargetForm {

  @NotNull(message = "{valid.dashboard.target.notNull}")
  @Min(value = 0, message = "{valid.dashboard.target.min}")
  private Integer target;
}
