package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.SpaceSummaryDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.SpaceForm;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

  private static final String USERNAME = "testuser";
  private static final UUID SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");

  @Mock private SpaceMapper spaceMapper;
  @Mock private UserMapper userMapper;
  @Mock private StockMapper stockMapper;
  @Mock private MessageSource messageSource;

  @InjectMocks private SpaceService spaceService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ReflectionTestUtils.setField(spaceService, "expiringSoonDays", 7);
  }

  @Test
  void delete_rejectsSpaceWithStock() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setExternalId(SPACE_EXTERNAL_ID);
    space.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(stockMapper.countBySpaceId(space.getId())).thenReturn(1);

    assertThatThrownBy(() -> spaceService.delete(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("error.space.hasStock");

    verify(spaceMapper, never()).deleteById(any());
  }

  @Test
  void delete_rejectsSpaceOwnedByAnotherUser() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setExternalId(SPACE_EXTERNAL_ID);
    space.setUserId(99L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> spaceService.delete(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(spaceMapper, never()).deleteById(any());
  }

  @Test
  void delete_rejectsNotFound() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> spaceService.delete(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(spaceMapper, never()).deleteById(any());
  }

  @Test
  void delete_removesSpaceWithoutStock() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setExternalId(SPACE_EXTERNAL_ID);
    space.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(stockMapper.countBySpaceId(space.getId())).thenReturn(0);

    spaceService.delete(SPACE_EXTERNAL_ID, USERNAME);

    verify(spaceMapper).deleteById(space.getId());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_throwsWhenUserNotFound() {
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.empty());

    SpaceForm form = new SpaceForm();
    form.setName("새 공간");

    assertThatThrownBy(() -> spaceService.create(USERNAME, form))
        .isInstanceOf(NoSuchElementException.class);

    verify(spaceMapper, never()).insertSpace(any());
  }

  @Test
  void create_insertsSpaceForUser() {
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    SpaceForm form = new SpaceForm();
    form.setName("새 공간");

    spaceService.create(USERNAME, form);

    ArgumentCaptor<SpaceDTO> captor = ArgumentCaptor.forClass(SpaceDTO.class);
    verify(spaceMapper).insertSpace(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getName()).isEqualTo("새 공간");
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void update_throwsWhenSpaceNotFound() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.empty());

    SpaceForm form = new SpaceForm();
    form.setName("변경명");

    assertThatThrownBy(() -> spaceService.update(SPACE_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(spaceMapper, never()).updateSpace(any());
  }

  @Test
  void update_throwsWhenAccessDenied() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setUserId(99L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    SpaceForm form = new SpaceForm();
    form.setName("변경명");

    assertThatThrownBy(() -> spaceService.update(SPACE_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(spaceMapper, never()).updateSpace(any());
  }

  @Test
  void update_updatesSpaceName() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setExternalId(SPACE_EXTERNAL_ID);
    space.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);
    SpaceDTO updated = new SpaceDTO();
    updated.setId(10L);
    updated.setName("변경명");

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID))
        .thenReturn(Optional.of(space))
        .thenReturn(Optional.of(updated));

    SpaceForm form = new SpaceForm();
    form.setName("변경명");

    spaceService.update(SPACE_EXTERNAL_ID, form, USERNAME);

    verify(spaceMapper).updateSpace(space);
    assertThat(space.getName()).isEqualTo("변경명");
  }

  // ── findByExternalId ──────────────────────────────────────────────────────

  @Test
  void findByExternalId_throwsWhenAccessDenied() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setUserId(99L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> spaceService.findByExternalId(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_returnsOwnedSpace() {
    SpaceDTO space = new SpaceDTO();
    space.setId(10L);
    space.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    SpaceDTO result = spaceService.findByExternalId(SPACE_EXTERNAL_ID, USERNAME);

    assertThat(result).isSameAs(space);
  }

  // ── findSummariesByExternalId ──────────────────────────────────────────────

  @Test
  void findSummaries_emptyList_returnsEmptyMapWithoutQuery() {
    Map<UUID, SpaceSummaryDTO> result = spaceService.findSummariesByExternalId(List.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(spaceMapper);
  }

  @Test
  void findSummaries_keysBySpaceExternalId() {
    SpaceDTO s1 = new SpaceDTO();
    s1.setId(10L);
    s1.setExternalId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    SpaceDTO s2 = new SpaceDTO();
    s2.setId(20L);
    s2.setExternalId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    SpaceSummaryDTO sum1 = new SpaceSummaryDTO();
    sum1.setSpaceExternalId(s1.getExternalId());
    sum1.setStockCount(5);
    SpaceSummaryDTO sum2 = new SpaceSummaryDTO();
    sum2.setSpaceExternalId(s2.getExternalId());
    sum2.setStockCount(9);

    when(spaceMapper.findSummariesBySpaceIds(eq(List.of(10L, 20L)), any(), any()))
        .thenReturn(List.of(sum1, sum2));

    Map<UUID, SpaceSummaryDTO> result = spaceService.findSummariesByExternalId(List.of(s1, s2));

    assertThat(result).containsOnlyKeys(s1.getExternalId(), s2.getExternalId());
    assertThat(result.get(s1.getExternalId()).getStockCount()).isEqualTo(5);
    assertThat(result.get(s2.getExternalId()).getStockCount()).isEqualTo(9);
  }

  @Test
  void findSummaries_passesSoonCutoffAsTodayPlusConfiguredDays() {
    SpaceDTO s1 = new SpaceDTO();
    s1.setId(10L);
    s1.setExternalId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    when(spaceMapper.findSummariesBySpaceIds(any(), any(), any())).thenReturn(List.of());

    spaceService.findSummariesByExternalId(List.of(s1));

    ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> soonCutoff = ArgumentCaptor.forClass(LocalDate.class);
    verify(spaceMapper)
        .findSummariesBySpaceIds(eq(List.of(10L)), today.capture(), soonCutoff.capture());
    assertThat(soonCutoff.getValue()).isEqualTo(today.getValue().plusDays(7));
  }
}
