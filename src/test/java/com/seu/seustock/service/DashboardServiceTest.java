package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seu.seustock.mapper.DashboardMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.DashboardSummaryDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private UserMapper userMapper;
  @Mock private DashboardMapper dashboardMapper;
  @Mock private org.springframework.context.MessageSource messageSource;

  @InjectMocks private DashboardService service;

  private UserDTO user(long id) {
    UserDTO u = new UserDTO();
    u.setId(id);
    u.setEmail("dash@test.com");
    return u;
  }

  @Test
  void getSummary_resolvesUserAndDelegatesToMapper() {
    when(userMapper.findByEmail("dash@test.com")).thenReturn(Optional.of(user(7L)));
    DashboardSummaryDTO dto = new DashboardSummaryDTO();
    when(dashboardMapper.findSummaryByUserId(eq(7L), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(dto);

    DashboardSummaryDTO result = service.getSummary("dash@test.com");

    assertThat(result).isSameAs(dto);
    verify(dashboardMapper).findSummaryByUserId(eq(7L), any(LocalDate.class), any(LocalDate.class));
  }

  @Test
  void getSummary_unknownUser_throws() {
    when(userMapper.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummary("ghost@test.com"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void updateTarget_resolvesUserAndPersists() {
    when(userMapper.findByEmail("dash@test.com")).thenReturn(Optional.of(user(7L)));

    service.updateTarget("dash@test.com", 50);

    verify(userMapper).updateTargetTotalStock(7L, 50);
  }

  // --- DashboardSummaryDTO 파생 계산 ---

  @Test
  void summary_noTarget_hasNoProcessing() {
    DashboardSummaryDTO s = new DashboardSummaryDTO();
    s.setTotalStockCount(100);
    s.setTargetTotalStock(null);

    assertThat(s.isTargetSet()).isFalse();
    assertThat(s.getToProcessCount()).isZero();
    assertThat(s.isTargetAchieved()).isFalse();
    assertThat(s.getOverPercent()).isZero();
  }

  @Test
  void summary_overTarget_computesExcessAndPercent() {
    DashboardSummaryDTO s = new DashboardSummaryDTO();
    s.setTotalStockCount(100);
    s.setTargetTotalStock(40);

    assertThat(s.isTargetSet()).isTrue();
    assertThat(s.getToProcessCount()).isEqualTo(60);
    assertThat(s.isTargetAchieved()).isFalse();
    assertThat(s.getOverPercent()).isEqualTo(60);
  }

  @Test
  void summary_atOrUnderTarget_isAchieved() {
    DashboardSummaryDTO s = new DashboardSummaryDTO();
    s.setTotalStockCount(30);
    s.setTargetTotalStock(40);

    assertThat(s.getToProcessCount()).isZero();
    assertThat(s.isTargetAchieved()).isTrue();
    assertThat(s.getOverPercent()).isZero();
  }
}
