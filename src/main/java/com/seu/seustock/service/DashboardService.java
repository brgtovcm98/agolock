package com.seu.seustock.service;

import com.seu.seustock.mapper.DashboardMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.DashboardSummaryDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 대시보드용 전체 재고 집계 및 총 보유 수량 목표 설정을 담당하는 서비스. */
@Service
@Slf4j
public class DashboardService extends BaseService {

  private final DashboardMapper dashboardMapper;

  @Value("${seustock.space.expiring-soon-days:7}")
  private int expiringSoonDays;

  public DashboardService(
      UserMapper userMapper, DashboardMapper dashboardMapper, MessageSource messageSource) {
    super(userMapper, messageSource);
    this.dashboardMapper = dashboardMapper;
  }

  /** 로그인 사용자의 전체 재고 롤업 집계를 반환한다. 만료 임박 창은 {@code today + expiringSoonDays}. */
  public DashboardSummaryDTO getSummary(String username) {
    UserDTO user = getUser(username);
    LocalDate today = LocalDate.now();
    return dashboardMapper.findSummaryByUserId(
        user.getId(), today, today.plusDays(expiringSoonDays));
  }

  /** 사용자별 총 보유 수량 목표를 설정한다. {@code null}이면 목표 해제. */
  @Transactional
  public void updateTarget(String username, Integer target) {
    UserDTO user = getUser(username);
    userMapper.updateTargetTotalStock(user.getId(), target);
    log.info("dashboard target updated userId={} target={}", user.getId(), target);
  }
}
