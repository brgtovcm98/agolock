package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.ShelfForm;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class ShelfServiceTest {

  private static final String USERNAME = "testuser";
  private static final UUID SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID SHELF_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");

  @Mock private ShelfMapper shelfMapper;
  @Mock private SpaceMapper spaceMapper;
  @Mock private UserMapper userMapper;
  @Mock private MessageSource messageSource;

  @InjectMocks private ShelfService shelfService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  // ── findAllBySpaceId ──────────────────────────────────────────────────────

  @Test
  void findAllBySpaceId_throwsWhenSpaceNotFound() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shelfService.findAllBySpaceId(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(shelfMapper, never()).findBySpaceId(any());
  }

  @Test
  void findAllBySpaceId_throwsWhenAccessDenied() {
    SpaceDTO space = space(10L, 99L); // 다른 사용자의 space
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> shelfService.findAllBySpaceId(SPACE_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(shelfMapper, never()).findBySpaceId(any());
  }

  @Test
  void findAllBySpaceId_returnsShelvesForOwnedSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    List<ShelfDTO> shelves = List.of(new ShelfDTO(), new ShelfDTO());

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findBySpaceId(10L)).thenReturn(shelves);

    List<ShelfDTO> result = shelfService.findAllBySpaceId(SPACE_EXTERNAL_ID, USERNAME);

    assertThat(result).hasSize(2);
    verify(shelfMapper).findBySpaceId(10L);
  }

  // ── findByExternalId ──────────────────────────────────────────────────────

  @Test
  void findByExternalId_throwsWhenShelfNotFound() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> shelfService.findByExternalId(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void findByExternalId_throwsWhenShelfBelongsToAnotherSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 99L); // 다른 space에 속한 shelf

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    assertThatThrownBy(
            () -> shelfService.findByExternalId(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_returnsShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    ShelfDTO result = shelfService.findByExternalId(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME);

    assertThat(result).isSameAs(shelf);
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_throwsWhenAccessDenied() {
    SpaceDTO space = space(10L, 99L);
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    ShelfForm form = new ShelfForm();
    form.setName("새 선반");

    assertThatThrownBy(() -> shelfService.create(SPACE_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(shelfMapper, never()).insertShelf(any());
  }

  @Test
  void create_insertsShelfUnderOwnedSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO created = shelf(20L, 10L);
    created.setName("새 선반");

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    // insertShelf는 void이므로 별도 stub 불필요
    when(shelfMapper.findById(any())).thenReturn(Optional.of(created));

    ShelfForm form = new ShelfForm();
    form.setName("새 선반");

    ShelfDTO result = shelfService.create(SPACE_EXTERNAL_ID, form, USERNAME);

    ArgumentCaptor<ShelfDTO> captor = ArgumentCaptor.forClass(ShelfDTO.class);
    verify(shelfMapper).insertShelf(captor.capture());
    assertThat(captor.getValue().getSpaceId()).isEqualTo(10L);
    assertThat(captor.getValue().getName()).isEqualTo("새 선반");
    assertThat(result).isSameAs(created);
  }

  // ── rename ────────────────────────────────────────────────────────────────

  @Test
  void rename_throwsWhenShelfBelongsToAnotherSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 99L); // 다른 space 소속

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    ShelfForm form = new ShelfForm();
    form.setName("변경명");

    assertThatThrownBy(
            () -> shelfService.rename(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(shelfMapper, never()).updateShelf(any());
  }

  @Test
  void rename_updatesShelfName() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    ShelfDTO updated = shelf(20L, 10L);
    updated.setName("변경명");

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(shelfMapper.findById(20L)).thenReturn(Optional.of(updated));

    ShelfForm form = new ShelfForm();
    form.setName("변경명");

    shelfService.rename(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, form, USERNAME);

    verify(shelfMapper).updateShelf(shelf);
    assertThat(shelf.getName()).isEqualTo("변경명");
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_throwsWhenAccessDenied() {
    SpaceDTO space = space(10L, 99L);
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> shelfService.delete(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(shelfMapper, never()).deleteById(any());
  }

  @Test
  void delete_throwsWhenShelfBelongsToAnotherSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 99L); // 다른 space 소속

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    assertThatThrownBy(() -> shelfService.delete(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(shelfMapper, never()).deleteById(any());
  }

  @Test
  void delete_removesShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    shelfService.delete(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, USERNAME);

    verify(shelfMapper).deleteById(20L);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static SpaceDTO space(Long id, Long userId) {
    SpaceDTO s = new SpaceDTO();
    s.setId(id);
    s.setUserId(userId);
    return s;
  }

  private static UserDTO user(Long id) {
    UserDTO u = new UserDTO();
    u.setId(id);
    return u;
  }

  private static ShelfDTO shelf(Long id, Long spaceId) {
    ShelfDTO s = new ShelfDTO();
    s.setId(id);
    s.setSpaceId(spaceId);
    return s;
  }
}
