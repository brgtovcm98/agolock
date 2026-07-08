package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.DashboardActivityDTO;
import com.seu.seustock.model.dto.DashboardSummaryDTO;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {

  /**
   * 사용자 전체 재고 롤업을 단일 행으로 집계한다. {@code today}/{@code soonCutoff}는 유통기한 임박 창을 DB 독립적으로 계산하기 위해 주입한다.
   */
  DashboardSummaryDTO findSummaryByUserId(
      @Param("userId") Long userId,
      @Param("today") LocalDate today,
      @Param("soonCutoff") LocalDate soonCutoff);

  /** 사용자의 전체 품목을 대상으로 한 최근 재고 트랜잭션을 최신순으로 {@code limit}건 반환한다. */
  List<DashboardActivityDTO> findRecentActivity(
      @Param("userId") Long userId, @Param("limit") int limit);
}
