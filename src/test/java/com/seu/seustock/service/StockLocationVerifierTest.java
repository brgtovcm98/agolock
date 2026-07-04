package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class StockLocationVerifierTest {

  private static final UUID SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID SHELF_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID BOX_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000001000");

  @Mock private SpaceMapper spaceMapper;
  @Mock private ShelfMapper shelfMapper;
  @Mock private BoxMapper boxMapper;
  @Mock private MessageSource messageSource;

  private StockLocationVerifier verifier;
  private UserDTO user;
  private SpaceDTO space;
  private ShelfDTO shelf;
  private BoxDTO box;

  @BeforeEach
  void setUp() {
    user = new UserDTO();
    user.setId(1L);
    space = new SpaceDTO();
    space.setId(10L);
    space.setUserId(user.getId());
    shelf = new ShelfDTO();
    shelf.setId(100L);
    shelf.setSpaceId(space.getId());
    box = new BoxDTO();
    box.setId(1000L);
    box.setShelfId(shelf.getId());

    verifier = new StockLocationVerifier(spaceMapper, shelfMapper, boxMapper, messageSource);
  }

  private void stubMessageSource() {
    when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void resolve_returnsVerifiedSpaceShelfAndBox() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    VerifiedStockLocation result =
        verifier.resolve(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, user);

    assertThat(result.space()).isSameAs(space);
    assertThat(result.shelf()).isSameAs(shelf);
    assertThat(result.box()).isSameAs(box);
    assertThat(result.shelfId()).isEqualTo(shelf.getId());
    assertThat(result.boxId()).isEqualTo(box.getId());
  }

  @Test
  void resolve_rejectsSpaceOwnedByAnotherUser() {
    stubMessageSource();
    space.setUserId(2L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    assertThatThrownBy(() -> verifier.resolve(SPACE_EXTERNAL_ID, null, null, user))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("error.403.title");
  }

  @Test
  void resolve_rejectsShelfFromDifferentSpace() {
    stubMessageSource();
    shelf.setSpaceId(20L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));

    assertThatThrownBy(() -> verifier.resolve(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, null, user))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("error.403.title");
  }

  @Test
  void resolve_rejectsBoxWithoutShelf() {
    stubMessageSource();
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    assertThatThrownBy(() -> verifier.resolve(SPACE_EXTERNAL_ID, null, BOX_EXTERNAL_ID, user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.box.requiresShelf");
  }

  @Test
  void resolve_rejectsBoxFromDifferentShelf() {
    stubMessageSource();
    box.setShelfId(200L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));

    assertThatThrownBy(
            () -> verifier.resolve(SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID, user))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("error.403.title");
  }
}
