package com.seu.seustock.service;

import com.seu.seustock.mapper.DashboardMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.DashboardActivityDTO;
import com.seu.seustock.model.dto.DashboardSpaceRowDTO;
import com.seu.seustock.model.dto.DashboardSummaryDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.SpaceSummaryDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 대시보드용 전체 재고 집계 및 총 보유 수량 목표 설정을 담당하는 서비스. */
@Service
@Slf4j
public class DashboardService extends BaseService {

  private static final int RECENT_ACTIVITY_LIMIT = 8;

  private final DashboardMapper dashboardMapper;
  private final SpaceMapper spaceMapper;

  @Value("${seustock.space.expiring-soon-days:7}")
  private int expiringSoonDays;

  public DashboardService(
      UserMapper userMapper,
      DashboardMapper dashboardMapper,
      SpaceMapper spaceMapper,
      MessageSource messageSource) {
    super(userMapper, messageSource);
    this.dashboardMapper = dashboardMapper;
    this.spaceMapper = spaceMapper;
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

  /**
   * 로그인 사용자의 공간별 요약 위젯 데이터를 반환한다. 주의(임박/만료/파손·분실)가 필요한 공간, 재고 수 순으로 정렬한다. {@code SpaceService}가 공간
   * 목록 페이지에 쓰는 것과 동일한 {@code findByUserId}+{@code findSummariesBySpaceIds} 조합을 재사용한다.
   */
  public List<DashboardSpaceRowDTO> getSpaceSnapshot(String username) {
    UserDTO user = getUser(username);
    List<SpaceDTO> spaces = spaceMapper.findByUserId(user.getId());
    if (spaces.isEmpty()) {
      return List.of();
    }
    LocalDate today = LocalDate.now();
    List<Long> spaceIds = spaces.stream().map(SpaceDTO::getId).toList();
    Map<UUID, SpaceSummaryDTO> summariesByExternalId =
        spaceMapper
            .findSummariesBySpaceIds(spaceIds, today, today.plusDays(expiringSoonDays))
            .stream()
            .collect(Collectors.toMap(SpaceSummaryDTO::getSpaceExternalId, Function.identity()));

    return spaces.stream()
        .map(
            space -> {
              SpaceSummaryDTO summary = summariesByExternalId.get(space.getExternalId());
              DashboardSpaceRowDTO row = new DashboardSpaceRowDTO();
              row.setSpaceExternalId(space.getExternalId());
              row.setName(space.getName());
              if (summary != null) {
                row.setStockCount(summary.getStockCount());
                row.setExpiringCount(summary.getExpiringCount());
                row.setExpiredCount(summary.getExpiredCount());
                row.setDamagedLostCount(summary.getDamagedLostCount());
              }
              return row;
            })
        .sorted(
            Comparator.comparing(DashboardSpaceRowDTO::isNeedsAttention)
                .reversed()
                .thenComparing(
                    Comparator.comparingInt(DashboardSpaceRowDTO::getStockCount).reversed()))
        .toList();
  }

  /** 로그인 사용자의 최근 입출고 활동을 최신순으로 반환한다. */
  public List<DashboardActivityDTO> getRecentActivity(String username) {
    UserDTO user = getUser(username);
    return dashboardMapper.findRecentActivity(user.getId(), RECENT_ACTIVITY_LIMIT);
  }
}
