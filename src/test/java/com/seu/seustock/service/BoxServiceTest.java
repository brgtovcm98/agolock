package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.BoxForm;
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
class BoxServiceTest {

  private static final String USERNAME = "testuser";
  private static final UUID SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID SHELF_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID BOX_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");

  @Mock private BoxMapper boxMapper;
  @Mock private ShelfMapper shelfMapper;
  @Mock private SpaceMapper spaceMapper;
  @Mock private UserMapper userMapper;
  @Mock private MessageSource messageSource;

  @InjectMocks private BoxService boxService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  // ── findByExternalId ──────────────────────────────────────────────────────

  @Test
  void findByExternalId_throwsWhenSpaceNotFound() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                boxService.findByExternalId(
                    SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void findByExternalId_throwsWhenAccessDenied() {
    SpaceDTO space = space(10L, 99L); // 타 사용자 space
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                boxService.findByExternalId(
                    SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_throwsWhenShelfBelongsToAnotherSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 99L); // 다른 space 소속 shelf

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    assertThatThrownBy(
            () ->
                boxService.findByExternalId(
                    SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_throwsWhenBoxBelongsToAnotherShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 99L); // 다른 shelf 소속 box

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    assertThatThrownBy(
            () ->
                boxService.findByExternalId(
                    SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_returnsBox() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 20L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    BoxDTO result =
        boxService.findByExternalId(
            SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME);

    assertThat(result).isSameAs(box);
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_throwsWhenAccessDenied() {
    SpaceDTO space = space(10L, 99L);
    UserDTO user = user(1L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    BoxForm form = new BoxForm();
    form.setName("새 박스");

    assertThatThrownBy(
            () -> boxService.create(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(boxMapper, never()).insertBox(any());
  }

  @Test
  void create_throwsWhenShelfBelongsToAnotherSpace() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 99L); // 계층 불일치

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    BoxForm form = new BoxForm();
    form.setName("새 박스");

    assertThatThrownBy(
            () -> boxService.create(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(boxMapper, never()).insertBox(any());
  }

  @Test
  void create_insertsBoxUnderOwnedShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO created = box(30L, 20L);
    created.setName("새 박스");

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findById(any())).thenReturn(Optional.of(created));

    BoxForm form = new BoxForm();
    form.setName("새 박스");

    BoxDTO result = boxService.create(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, form, USERNAME);

    ArgumentCaptor<BoxDTO> captor = ArgumentCaptor.forClass(BoxDTO.class);
    verify(boxMapper).insertBox(captor.capture());
    assertThat(captor.getValue().getShelfId()).isEqualTo(20L);
    assertThat(captor.getValue().getName()).isEqualTo("새 박스");
    assertThat(result).isSameAs(created);
  }

  // ── rename ────────────────────────────────────────────────────────────────

  @Test
  void rename_throwsWhenBoxBelongsToAnotherShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 99L); // 다른 shelf 소속

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    BoxForm form = new BoxForm();
    form.setName("변경명");

    assertThatThrownBy(
            () ->
                boxService.rename(
                    SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(boxMapper, never()).updateBox(any());
  }

  @Test
  void rename_updatesBoxName() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 20L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    BoxForm form = new BoxForm();
    form.setName("변경명");

    boxService.rename(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, form, USERNAME);

    verify(boxMapper).updateBox(box);
    assertThat(box.getName()).isEqualTo("변경명");
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_throwsWhenBoxBelongsToAnotherShelf() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 99L); // 다른 shelf 소속

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    assertThatThrownBy(
            () ->
                boxService.delete(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(boxMapper, never()).deleteById(any());
  }

  @Test
  void delete_removesBox() {
    SpaceDTO space = space(10L, 1L);
    UserDTO user = user(1L);
    ShelfDTO shelf = shelf(20L, 10L);
    BoxDTO box = box(30L, 20L);

    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    boxService.delete(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, USERNAME);

    verify(boxMapper).deleteById(30L);
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

  private static BoxDTO box(Long id, Long shelfId) {
    BoxDTO b = new BoxDTO();
    b.setId(id);
    b.setShelfId(shelfId);
    return b;
  }
}
