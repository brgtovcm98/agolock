package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.ItemImageMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.StockTransactionMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.ItemForm;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

  private static final String USERNAME = "testuser";
  private static final UUID ITEM_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock private ItemMapper itemMapper;
  @Mock private UserMapper userMapper;
  @Mock private StockMapper stockMapper;
  @Mock private ItemImageMapper itemImageMapper;
  @Mock private ImageStorageService imageStorageService;
  @Mock private StockTransactionMapper transactionMapper;
  @Mock private MessageSource messageSource;

  @InjectMocks private ItemService itemService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void delete_rejectsItemWithCurrentStock() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(stockMapper.countInStockByItemId(item.getId())).thenReturn(1);

    assertThatThrownBy(() -> itemService.delete(ITEM_EXTERNAL_ID, USERNAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("error.item.hasStock");

    verify(itemMapper, never()).deactivateById(any());
    verify(itemMapper, never()).deleteById(any());
  }

  @Test
  void delete_deactivatesItemWithOnlyHistory() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(stockMapper.countInStockByItemId(item.getId())).thenReturn(0);
    when(stockMapper.countByItemId(item.getId())).thenReturn(1);

    itemService.delete(ITEM_EXTERNAL_ID, USERNAME);

    verify(itemMapper).deactivateById(item.getId());
    verify(itemMapper, never()).deleteById(any());
  }

  @Test
  void delete_rejectsItemOwnedByAnotherUser() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(99L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> itemService.delete(ITEM_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(itemMapper, never()).deleteById(any());
  }

  @Test
  void delete_rejectsNotFound() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> itemService.delete(ITEM_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(itemMapper, never()).deleteById(any());
  }

  @Test
  void delete_removesItemWithoutStock() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(stockMapper.countInStockByItemId(item.getId())).thenReturn(0);
    when(stockMapper.countByItemId(item.getId())).thenReturn(0);

    itemService.delete(ITEM_EXTERNAL_ID, USERNAME);

    verify(itemMapper, never()).deactivateById(any());
    verify(itemMapper).deleteById(item.getId());
  }

  @Test
  void update_reusesExistingImageAssociationInsteadOfInsertingDuplicate() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);
    ImageDTO image = new ImageDTO();
    image.setId(20L);
    MultipartFile imageFile = mock(MultipartFile.class);
    ItemForm form = new ItemForm();
    form.setName("수정품목");
    form.setDescription("수정설명");
    form.setImageFile(imageFile);
    form.setImageHash("abc123");

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(imageStorageService.store(imageFile, user, "abc123")).thenReturn(image);
    when(itemImageMapper.countByItemIdAndImageId(item.getId(), image.getId())).thenReturn(1);

    itemService.update(ITEM_EXTERNAL_ID, form, USERNAME);

    verify(itemImageMapper).unsetPrimaryByItemId(item.getId());
    verify(itemImageMapper).updateItemImage(item.getId(), image.getId(), 0, true);
    verify(itemImageMapper, never()).insertItemImage(any(), any(), anyInt(), anyBoolean());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_insertsItemWithoutImage() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    ItemDTO created = new ItemDTO();
    created.setId(10L);
    created.setUserId(1L);
    created.setName("테스트 품목");

    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(imageStorageService.store(null, user, null)).thenReturn(null);
    when(itemMapper.findById(any())).thenReturn(Optional.of(created));

    ItemForm form = new ItemForm();
    form.setName("테스트 품목");

    ItemDTO result = itemService.create(USERNAME, form);

    verify(itemMapper).insertItem(any());
    verify(itemImageMapper, never()).insertItemImage(any(), any(), anyInt(), anyBoolean());
    assertThat(result).isSameAs(created);
  }

  @Test
  void create_attachesPrimaryImageWhenProvided() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    ItemDTO created = new ItemDTO();
    created.setId(10L);
    ImageDTO image = new ImageDTO();
    image.setId(20L);
    MultipartFile imageFile = mock(MultipartFile.class);

    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(imageStorageService.store(imageFile, user, "hash123")).thenReturn(image);
    when(itemImageMapper.countByItemIdAndImageId(any(), eq(20L))).thenReturn(0);
    when(itemMapper.findById(any())).thenReturn(Optional.of(created));

    ItemForm form = new ItemForm();
    form.setName("이미지 품목");
    form.setImageFile(imageFile);
    form.setImageHash("hash123");

    itemService.create(USERNAME, form);

    verify(itemImageMapper).insertItemImage(any(), eq(20L), eq(0), eq(true));
  }

  @Test
  void create_setsPriceOnInsertedItem() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(imageStorageService.store(null, user, null)).thenReturn(null);
    when(itemMapper.findById(any())).thenReturn(Optional.of(new ItemDTO()));

    ItemForm form = new ItemForm();
    form.setName("가격 품목");
    form.setPrice(new BigDecimal("12000"));

    itemService.create(USERNAME, form);

    var captor = forClass(ItemDTO.class);
    verify(itemMapper).insertItem(captor.capture());
    assertThat(captor.getValue().getPrice()).isEqualByComparingTo("12000");
  }

  @Test
  void update_setsPriceOnItem() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(imageStorageService.store(null, user, null)).thenReturn(null);

    ItemForm form = new ItemForm();
    form.setName("수정품목");
    form.setPrice(new BigDecimal("15000"));

    itemService.update(ITEM_EXTERNAL_ID, form, USERNAME);

    var captor = forClass(ItemDTO.class);
    verify(itemMapper).updateItem(captor.capture());
    assertThat(captor.getValue().getPrice()).isEqualByComparingTo("15000");
  }

  // ── findByExternalId ──────────────────────────────────────────────────────

  @Test
  void findByExternalId_throwsWhenAccessDenied() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(99L); // 타 사용자 소유
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> itemService.findByExternalId(ITEM_EXTERNAL_ID, USERNAME))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void findByExternalId_returnsOwnedItem() {
    ItemDTO item = new ItemDTO();
    item.setId(10L);
    item.setExternalId(ITEM_EXTERNAL_ID);
    item.setUserId(1L);
    UserDTO user = new UserDTO();
    user.setId(1L);

    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    ItemDTO result = itemService.findByExternalId(ITEM_EXTERNAL_ID, USERNAME);

    assertThat(result).isSameAs(item);
  }
}
