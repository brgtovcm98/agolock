package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.*;
import com.seu.seustock.model.dto.*;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TrackingMode;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.model.form.QuickStockForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.model.form.StockInOutForm;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.model.form.StockUpdateForm;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

  private static final String USERNAME = "testuser";
  private static final UUID ITEM_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_ITEM_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID OTHER_SPACE_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID SHELF_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID OTHER_SHELF_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID BOX_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000001000");
  private static final UUID OTHER_BOX_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000001001");
  private static final UUID STOCK_EXTERNAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000010000");

  @Mock private StockMapper stockMapper;
  @Mock private StockTransactionMapper transactionMapper;
  @Mock private ItemMapper itemMapper;
  @Mock private ItemLotMapper itemLotMapper;
  @Mock private ItemImageMapper itemImageMapper;
  @Mock private SpaceMapper spaceMapper;
  @Mock private ShelfMapper shelfMapper;
  @Mock private BoxMapper boxMapper;
  @Mock private UserMapper userMapper;
  @Mock private ImageStorageService imageStorageService;
  @Mock private MessageSource messageSource;

  private ServiceHelpers helpers;
  @Spy private SerialNumberGenerator serialNumberGenerator = new SerialNumberGenerator();
  @Spy private LotNumberGenerator lotNumberGenerator = new LotNumberGenerator();

  @Spy
  private Clock clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneId.systemDefault());

  private StockLocationVerifier locationVerifier;
  private StockInboundPreparer inboundPreparer;
  private StockTransactionRecorder transactionRecorder;
  private StockService stockService;

  private UserDTO user;
  private ItemDTO item;
  private ItemDTO otherUserItem;
  private SpaceDTO space;
  private SpaceDTO otherSpace;
  private ShelfDTO shelf;
  private ShelfDTO otherSpaceShelf;
  private BoxDTO box;
  private BoxDTO otherShelfBox;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(
            invocation ->
                switch ((String) invocation.getArgument(0)) {
                  case "stock.memo.initial" -> "초기 등록";
                  case "stock.memo.quick" -> "빠른 등록";
                  case "enum.TransactionMemoMaster.PURCHASE_IN" -> "구매 입고";
                  case "enum.TransactionMemoMaster.RETURN_IN" -> "반품 입고";
                  case "enum.TransactionMemoMaster.FOUND_IN" -> "재고 발견";
                  case "enum.TransactionMemoMaster.ADJUSTMENT_IN" -> "수량 보정";
                  case "enum.TransactionMemoMaster.USE_OUT" -> "사용 출고";
                  case "enum.TransactionMemoMaster.SALES_OUT" -> "판매 출고";
                  case "enum.TransactionMemoMaster.DISPOSAL_OUT" -> "폐기 출고";
                  case "enum.TransactionMemoMaster.LOST_OUT" -> "분실 처리";
                  default -> invocation.getArgument(0);
                });

    user = user(1L);
    item = item(10L, ITEM_EXTERNAL_ID, user.getId());
    otherUserItem = item(20L, OTHER_ITEM_EXTERNAL_ID, 2L);
    space = space(100L, SPACE_EXTERNAL_ID, user.getId());
    otherSpace = space(200L, OTHER_SPACE_EXTERNAL_ID, user.getId());
    shelf = shelf(1000L, SHELF_EXTERNAL_ID, space.getId());
    otherSpaceShelf = shelf(1001L, OTHER_SHELF_EXTERNAL_ID, otherSpace.getId());
    box = box(10000L, BOX_EXTERNAL_ID, shelf.getId());
    otherShelfBox = box(10001L, OTHER_BOX_EXTERNAL_ID, otherSpaceShelf.getId());

    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));

    locationVerifier =
        new StockLocationVerifier(spaceMapper, shelfMapper, boxMapper, messageSource);
    inboundPreparer =
        new StockInboundPreparer(
            stockMapper,
            itemMapper,
            itemLotMapper,
            serialNumberGenerator,
            lotNumberGenerator,
            clock,
            messageSource);
    transactionRecorder = new StockTransactionRecorder(transactionMapper);
    helpers = new ServiceHelpers(userMapper, messageSource);
    stockService =
        new StockService(
            stockMapper,
            transactionMapper,
            itemMapper,
            itemImageMapper,
            imageStorageService,
            locationVerifier,
            inboundPreparer,
            transactionRecorder,
            helpers);
  }

  @Test
  void create_rejectsItemOwnedByAnotherUser() {
    when(itemMapper.findByExternalId(OTHER_ITEM_EXTERNAL_ID))
        .thenReturn(Optional.of(otherUserItem));

    assertThatThrownBy(
            () ->
                stockService.create(
                    stockForm(OTHER_ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null), USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_rejectsInactiveItem() {
    item.setActive(false);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                stockService.create(
                    stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null), USERNAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("error.item.inactive");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_rejectsShelfFromDifferentSpace() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(OTHER_SHELF_EXTERNAL_ID))
        .thenReturn(Optional.of(otherSpaceShelf));

    assertThatThrownBy(
            () ->
                stockService.create(
                    stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, OTHER_SHELF_EXTERNAL_ID, null),
                    USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_rejectsBoxWithoutShelf() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    assertThatThrownBy(
            () ->
                stockService.create(
                    stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, BOX_EXTERNAL_ID),
                    USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.box.requiresShelf");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_rejectsBoxFromDifferentShelf() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(OTHER_BOX_EXTERNAL_ID)).thenReturn(Optional.of(otherShelfBox));

    assertThatThrownBy(
            () ->
                stockService.create(
                    stockForm(
                        ITEM_EXTERNAL_ID,
                        SPACE_EXTERNAL_ID,
                        SHELF_EXTERNAL_ID,
                        OTHER_BOX_EXTERNAL_ID),
                    USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_usesVerifiedLocationIds() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));
    doAnswer(
            invocation -> {
              List<StockDTO> stocks = invocation.getArgument(0);
              stocks.forEach(s -> s.setId(500L));
              return null;
            })
        .when(stockMapper)
        .insertStocks(anyList());

    stockService.create(
        stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, SHELF_EXTERNAL_ID, BOX_EXTERNAL_ID),
        USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> stocksCaptor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(stocksCaptor.capture());
    assertThat(stocksCaptor.getValue()).hasSize(1);
    StockDTO captured = stocksCaptor.getValue().get(0);
    assertThat(captured.getItemId()).isEqualTo(item.getId());
    assertThat(captured.getSpaceId()).isEqualTo(space.getId());
    assertThat(captured.getShelfId()).isEqualTo(shelf.getId());
    assertThat(captured.getBoxId()).isEqualTo(box.getId());
    verify(transactionMapper).insertTransactions(anyList());
  }

  @Test
  void dispatchUnits_rejectsChangedStockState() {
    StockInOutForm form = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    StockDTO stock = new StockDTO();
    stock.setId(700L);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findDispatchableByItemAndSpace(item.getId(), space.getId(), false))
        .thenReturn(List.of(stock));
    when(stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED)).thenReturn(0);

    assertThatThrownBy(() -> stockService.dispatchUnits(form, USERNAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("error.stock.statusChanged");

    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void deleteUnits_rejectsManipulatedLocation() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(OTHER_SHELF_EXTERNAL_ID))
        .thenReturn(Optional.of(otherSpaceShelf));

    assertThatThrownBy(
            () ->
                stockService.deleteUnits(
                    ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, OTHER_SHELF_EXTERNAL_ID, null, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).deleteInStockByItemAndShelf(any(), any());
  }

  @Test
  void deleteUnit_deletesOwnedInStockUnit() {
    when(stockMapper.deleteInStockByExternalIdAndUserId(STOCK_EXTERNAL_ID, user.getId()))
        .thenReturn(1);

    stockService.deleteUnit(STOCK_EXTERNAL_ID, USERNAME);

    verify(stockMapper).deleteInStockByExternalIdAndUserId(STOCK_EXTERNAL_ID, user.getId());
  }

  @Test
  void deleteUnit_rejectsMissingUnauthorizedOrNonInStockUnit() {
    when(stockMapper.deleteInStockByExternalIdAndUserId(STOCK_EXTERNAL_ID, user.getId()))
        .thenReturn(0);

    assertThatThrownBy(() -> stockService.deleteUnit(STOCK_EXTERNAL_ID, USERNAME))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("error.stock.notFound");
  }

  @Test
  void findMemoSuggestions_returnsFrequentUserMemos() {
    when(transactionMapper.findFrequentMemosByUserIdAndType(user.getId(), TransactionType.OUT, 4))
        .thenReturn(List.of("사용 출고", "폐기 출고"));

    List<String> suggestions = stockService.findMemoSuggestions(TransactionType.OUT, USERNAME);

    assertThat(suggestions).containsExactly("사용 출고", "폐기 출고", "판매 출고", "분실 처리");
  }

  @Test
  void findMemoSuggestions_displaysStoredMasterMemoValuesAsMessages() {
    when(transactionMapper.findFrequentMemosByUserIdAndType(user.getId(), TransactionType.IN, 4))
        .thenReturn(List.of("PURCHASE_IN", "enum.TransactionMemoMaster.RETURN_IN"));

    List<String> suggestions = stockService.findMemoSuggestions(TransactionType.IN, USERNAME);

    assertThat(suggestions).containsExactly("구매 입고", "반품 입고", "재고 발견", "수량 보정");
  }

  @Test
  void findMemoSuggestions_fallsBackToMasterMemosWhenUserHasNoHistory() {
    when(transactionMapper.findFrequentMemosByUserIdAndType(user.getId(), TransactionType.IN, 4))
        .thenReturn(List.of());

    List<String> suggestions = stockService.findMemoSuggestions(TransactionType.IN, USERNAME);

    assertThat(suggestions).containsExactly("구매 입고", "반품 입고", "재고 발견", "수량 보정");
  }

  @Test
  void create_happyPath_spaceOnly() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    doAnswer(
            invocation -> {
              List<StockDTO> stocks = invocation.getArgument(0);
              stocks.forEach(s -> s.setId(501L));
              return null;
            })
        .when(stockMapper)
        .insertStocks(anyList());

    stockService.create(stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null), USERNAME);

    verify(stockMapper).insertStocks(anyList());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockTransactionDTO>> txCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionMapper).insertTransactions(txCaptor.capture());
    assertThat(txCaptor.getValue()).hasSize(1);
    assertThat(txCaptor.getValue().get(0).getMemo()).isEqualTo("초기 등록");
  }

  @Test
  void create_generatesAutoSerialsAndLot() {
    item.setSerialMode(TrackingMode.AUTO);
    item.setSerialPrefix("SEU-");
    item.setSerialPaddingLength(4);
    item.setSerialIncrementUnit(1);
    item.setSerialNextSequence(0);
    item.setLotMode(TrackingMode.AUTO);
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(true);
    item.setExpirationPeriodDays(30);
    StockForm form = stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null);
    form.setCount(3);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findExistingSerialNumbers(eq(item.getId()), anyList())).thenReturn(List.of());
    when(itemLotMapper.findByItemIdAndLotNumber(item.getId(), "20260531-001"))
        .thenReturn(Optional.empty());
    doAnswer(
            invocation -> {
              ItemLotDTO lot = invocation.getArgument(0);
              lot.setId(900L);
              return null;
            })
        .when(itemLotMapper)
        .insertLot(any());
    when(itemLotMapper.findById(900L))
        .thenAnswer(
            invocation -> {
              ItemLotDTO lot = new ItemLotDTO();
              lot.setId(900L);
              lot.setItemId(item.getId());
              lot.setLotNumber("20260531-001");
              lot.setExpirationDate(LocalDate.of(2026, 6, 30));
              return Optional.of(lot);
            });
    doAnswer(
            invocation -> {
              List<StockDTO> stocks = invocation.getArgument(0);
              long id = 500L;
              for (StockDTO stock : stocks) {
                stock.setId(++id);
              }
              return null;
            })
        .when(stockMapper)
        .insertStocks(anyList());

    stockService.create(form, USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(captor.capture());
    assertThat(captor.getValue())
        .extracting(StockDTO::getSerialNumber)
        .containsExactly("SEU-0001", "SEU-0002", "SEU-0003");
    assertThat(captor.getValue())
        .allSatisfy(
            stock -> {
              assertThat(stock.getLotId()).isEqualTo(900L);
              assertThat(stock.getLotNumber()).isEqualTo("20260531-001");
              assertThat(stock.getExpirationDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            });
    verify(itemMapper).updateSerialNextSequence(item.getId(), 3);
    verify(itemMapper).updateLotSequence(item.getId(), "20260531", 1);
  }

  @Test
  void addUnits_manualSerialsOverrideCountAndManualLotReusesExistingLot() {
    item.setSerialMode(TrackingMode.MANUAL);
    item.setLotMode(TrackingMode.MANUAL);
    ItemLotDTO existingLot = new ItemLotDTO();
    existingLot.setId(901L);
    existingLot.setLotNumber("LOT-A");
    existingLot.setExpirationDate(LocalDate.of(2026, 12, 31));
    StockInOutForm form = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    form.setCount(1);
    form.setSerialNumbersText("SN-1\nSN-2\n");
    form.setLotNumber("LOT-A");
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findExistingSerialNumbers(eq(item.getId()), anyList())).thenReturn(List.of());
    when(itemLotMapper.findByItemIdAndLotNumber(item.getId(), "LOT-A"))
        .thenReturn(Optional.of(existingLot));

    stockService.addUnits(form, USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue())
        .extracting(StockDTO::getSerialNumber)
        .containsExactly("SN-1", "SN-2");
    assertThat(captor.getValue()).allSatisfy(stock -> assertThat(stock.getLotId()).isEqualTo(901L));
    verify(itemLotMapper, never()).insertLot(any());
  }

  @Test
  void create_rejectsDuplicateManualSerials() {
    item.setSerialMode(TrackingMode.MANUAL);
    StockForm form = stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null);
    form.setSerialNumbersText("SN-1\nSN-1");
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    assertThatThrownBy(() -> stockService.create(form, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.duplicate");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void create_leavesPriceNullWhenFormPriceNull() {
    item.setPrice(new BigDecimal("5000"));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    stockService.create(stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null), USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(captor.capture());
    assertThat(captor.getValue().get(0).getPrice()).isNull();
  }

  @Test
  void create_usesFormPriceOverItemPrice() {
    item.setPrice(new BigDecimal("5000"));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    StockForm form = stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null);
    form.setPrice(new BigDecimal("8000"));
    stockService.create(form, USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(captor.capture());
    assertThat(captor.getValue().get(0).getPrice()).isEqualByComparingTo("8000");
  }

  @Test
  void addUnits_leavesPriceNullWhenFormPriceNull() {
    item.setPrice(new BigDecimal("5000"));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    stockService.addUnits(stockInOutForm(SPACE_EXTERNAL_ID, null, null), USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(captor.capture());
    assertThat(captor.getValue().get(0).getPrice()).isNull();
  }

  @Test
  void addUnits_insertsStockAndTransactionForEachUnit() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    doAnswer(
            invocation -> {
              List<StockDTO> stocks = invocation.getArgument(0);
              long id = 500L;
              for (StockDTO s : stocks) {
                s.setId(++id);
              }
              return null;
            })
        .when(stockMapper)
        .insertStocks(anyList());

    StockInOutForm form = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    form.setCount(3);
    stockService.addUnits(form, USERNAME);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> stocksCaptor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(stocksCaptor.capture());
    assertThat(stocksCaptor.getValue()).hasSize(3);
    verify(transactionMapper).insertTransactions(anyList());
  }

  @Test
  void addUnits_rejectsItemOwnedByAnotherUser() {
    when(itemMapper.findByExternalId(OTHER_ITEM_EXTERNAL_ID))
        .thenReturn(Optional.of(otherUserItem));

    StockInOutForm form = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    form.setItemExternalId(OTHER_ITEM_EXTERNAL_ID);
    assertThatThrownBy(() -> stockService.addUnits(form, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void dispatchUnits_updatesStatusAndRecordsTransaction() {
    StockDTO unit = new StockDTO();
    unit.setId(700L);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findDispatchableByItemAndSpace(item.getId(), space.getId(), false))
        .thenReturn(List.of(unit));
    when(stockMapper.updateStatusIfInStock(unit.getId(), StockStatus.DISPATCHED)).thenReturn(1);

    stockService.dispatchUnits(stockInOutForm(SPACE_EXTERNAL_ID, null, null), USERNAME);

    verify(stockMapper).updateStatusIfInStock(unit.getId(), StockStatus.DISPATCHED);
    ArgumentCaptor<StockTransactionDTO> txCaptor =
        ArgumentCaptor.forClass(StockTransactionDTO.class);
    verify(transactionMapper).insertTransaction(txCaptor.capture());
    assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.OUT);
  }

  @Test
  void changeStatus_rejectsInStockSelection() {
    assertThatThrownBy(
            () ->
                stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.IN_STOCK, null, USERNAME))
        .isInstanceOf(IllegalArgumentException.class);

    verify(stockMapper, never()).updateStatusAndMemoIfInStock(any(), any(), any(), any());
    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void changeStatus_rejectsItemOwnedByAnotherUser() {
    StockDTO stock = stock(700L);
    stock.setItemId(otherUserItem.getId());
    when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
    when(itemMapper.findById(otherUserItem.getId())).thenReturn(Optional.of(otherUserItem));

    assertThatThrownBy(
            () -> stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DAMAGED, null, USERNAME))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).updateStatusAndMemoIfInStock(any(), any(), any(), any());
    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void changeStatus_rejectsWhenNotInStock() {
    StockDTO stock = stock(700L);
    stock.setItemId(item.getId());
    stock.setMemo("기존 메모");
    when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
    when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
    when(stockMapper.updateStatusAndMemoIfInStock(
            eq(STOCK_EXTERNAL_ID), eq(user.getId()), eq(StockStatus.LOST), anyString()))
        .thenReturn(0);

    assertThatThrownBy(
            () -> stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.LOST, "분실 추정", USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void changeStatus_appendsMemoAndRecordsAdjustTransaction() {
    StockDTO stock = stock(700L);
    stock.setItemId(item.getId());
    stock.setMemo("기존 메모");
    when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
    when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
    when(stockMapper.updateStatusAndMemoIfInStock(
            eq(STOCK_EXTERNAL_ID), eq(user.getId()), eq(StockStatus.DAMAGED), anyString()))
        .thenReturn(1);

    stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DAMAGED, "파손 확인", USERNAME);

    String today = java.time.LocalDate.now().toString();
    ArgumentCaptor<String> memoCaptor = ArgumentCaptor.forClass(String.class);
    verify(stockMapper)
        .updateStatusAndMemoIfInStock(
            eq(STOCK_EXTERNAL_ID), eq(user.getId()), eq(StockStatus.DAMAGED), memoCaptor.capture());
    assertThat(memoCaptor.getValue()).isEqualTo("기존 메모\n[" + today + "] 파손 확인");

    ArgumentCaptor<StockTransactionDTO> txCaptor =
        ArgumentCaptor.forClass(StockTransactionDTO.class);
    verify(transactionMapper).insertTransaction(txCaptor.capture());
    assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.ADJUST);
    assertThat(txCaptor.getValue().getStockId()).isEqualTo(700L);
    assertThat(txCaptor.getValue().getMemo()).isEqualTo("파손 확인");
  }

  @Test
  void changeStatus_dispatchedUsesOutTransactionAndKeepsMemoWhenReasonBlank() {
    StockDTO stock = stock(700L);
    stock.setItemId(item.getId());
    stock.setMemo("기존 메모");
    when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
    when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
    when(stockMapper.updateStatusAndMemoIfInStock(
            eq(STOCK_EXTERNAL_ID), eq(user.getId()), eq(StockStatus.DISPATCHED), anyString()))
        .thenReturn(1);

    stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DISPATCHED, "   ", USERNAME);

    ArgumentCaptor<String> memoCaptor = ArgumentCaptor.forClass(String.class);
    verify(stockMapper)
        .updateStatusAndMemoIfInStock(
            eq(STOCK_EXTERNAL_ID),
            eq(user.getId()),
            eq(StockStatus.DISPATCHED),
            memoCaptor.capture());
    assertThat(memoCaptor.getValue()).isEqualTo("기존 메모");

    ArgumentCaptor<StockTransactionDTO> txCaptor =
        ArgumentCaptor.forClass(StockTransactionDTO.class);
    verify(transactionMapper).insertTransaction(txCaptor.capture());
    assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.OUT);
    assertThat(txCaptor.getValue().getMemo()).isEqualTo(StockStatus.DISPATCHED.getLabel());
  }

  @Test
  void dispatchUnits_rejectsInsufficientStock() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findDispatchableByItemAndSpace(item.getId(), space.getId(), false))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                stockService.dispatchUnits(stockInOutForm(SPACE_EXTERNAL_ID, null, null), USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.insufficient");

    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void moveUnits_updatesLocationAndRecordsMoveTransaction() {
    StockDTO unit1 = stock(701L);
    StockDTO unit2 = stock(702L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(stockMapper.findInStockByItemAndSpace(item.getId(), space.getId()))
        .thenReturn(List.of(unit1, unit2));
    when(stockMapper.updateLocationIfInStock(
            List.of(unit1.getId(), unit2.getId()), space.getId(), shelf.getId(), box.getId()))
        .thenReturn(2);

    stockService.moveUnits(
        stockMoveForm(
            SPACE_EXTERNAL_ID,
            null,
            null,
            SPACE_EXTERNAL_ID,
            SHELF_EXTERNAL_ID,
            BOX_EXTERNAL_ID,
            2),
        USERNAME);

    verify(stockMapper)
        .updateLocationIfInStock(
            List.of(unit1.getId(), unit2.getId()), space.getId(), shelf.getId(), box.getId());
    ArgumentCaptor<StockTransactionDTO> txCaptor =
        ArgumentCaptor.forClass(StockTransactionDTO.class);
    verify(transactionMapper, times(2)).insertTransaction(txCaptor.capture());
    assertThat(txCaptor.getAllValues())
        .extracting(StockTransactionDTO::getTransactionType)
        .containsOnly(TransactionType.MOVE);
    assertThat(txCaptor.getAllValues())
        .allSatisfy(
            tx -> {
              assertThat(tx.getFromSpaceId()).isEqualTo(space.getId());
              assertThat(tx.getFromShelfId()).isNull();
              assertThat(tx.getFromBoxId()).isNull();
              assertThat(tx.getToSpaceId()).isEqualTo(space.getId());
              assertThat(tx.getToShelfId()).isEqualTo(shelf.getId());
              assertThat(tx.getToBoxId()).isEqualTo(box.getId());
            });
  }

  @Test
  void moveUnits_rejectsSameLocation() {
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    assertThatThrownBy(
            () ->
                stockService.moveUnits(
                    stockMoveForm(SPACE_EXTERNAL_ID, null, null, SPACE_EXTERNAL_ID, null, null, 1),
                    USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.move.sameLocation");

    verify(stockMapper, never()).updateLocationIfInStock(any(), any(), any(), any());
  }

  @Test
  void moveUnits_rejectsInsufficientStock() {
    StockDTO unit = stock(701L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(stockMapper.findInStockByItemAndSpace(item.getId(), space.getId()))
        .thenReturn(List.of(unit));

    assertThatThrownBy(
            () ->
                stockService.moveUnits(
                    stockMoveForm(
                        SPACE_EXTERNAL_ID,
                        null,
                        null,
                        SPACE_EXTERNAL_ID,
                        SHELF_EXTERNAL_ID,
                        null,
                        2),
                    USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.insufficient");

    verify(stockMapper, never()).updateLocationIfInStock(any(), any(), any(), any());
    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void updateDetails_trimsBlankValuesAndReturnsUpdatedStock() {
    StockUpdateForm form = new StockUpdateForm();
    form.setSerialNumber(" SN-1 ");
    form.setLotNumber(" ");
    form.setMemo("  별도 보관  ");
    StockDetailDTO updated = new StockDetailDTO();
    updated.setExternalId(STOCK_EXTERNAL_ID);
    updated.setSerialNumber("SN-1");
    updated.setMemo("별도 보관");

    when(stockMapper.updateDetails(eq(STOCK_EXTERNAL_ID), eq(user.getId()), same(form)))
        .thenReturn(1);
    when(stockMapper.findDetailByExternalId(STOCK_EXTERNAL_ID, user.getId()))
        .thenReturn(Optional.of(updated));

    StockDetailDTO result = stockService.updateDetails(STOCK_EXTERNAL_ID, form, USERNAME);

    assertThat(result.getSerialNumber()).isEqualTo("SN-1");
    assertThat(result.getMemo()).isEqualTo("별도 보관");
    assertThat(form.getSerialNumber()).isEqualTo("SN-1");
    assertThat(form.getLotNumber()).isNull();
    assertThat(form.getMemo()).isEqualTo("별도 보관");
  }

  @Test
  void updateDetails_rejectsMissingOrUnauthorizedStock() {
    StockUpdateForm form = new StockUpdateForm();
    when(stockMapper.updateDetails(eq(STOCK_EXTERNAL_ID), eq(user.getId()), same(form)))
        .thenReturn(0);

    assertThatThrownBy(() -> stockService.updateDetails(STOCK_EXTERNAL_ID, form, USERNAME))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("error.stock.notFound");
  }

  private StockForm stockForm(
      UUID itemExternalId, UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId) {
    StockForm form = new StockForm();
    form.setItemExternalId(itemExternalId);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    form.setCount(1);
    return form;
  }

  private StockInOutForm stockInOutForm(
      UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId) {
    StockInOutForm form = new StockInOutForm();
    form.setItemExternalId(ITEM_EXTERNAL_ID);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    form.setCount(1);
    return form;
  }

  private StockMoveForm stockMoveForm(
      UUID sourceSpaceExternalId,
      UUID sourceShelfExternalId,
      UUID sourceBoxExternalId,
      UUID targetSpaceExternalId,
      UUID targetShelfExternalId,
      UUID targetBoxExternalId,
      int count) {
    StockMoveForm form = new StockMoveForm();
    form.setSourceSpaceExternalId(sourceSpaceExternalId);
    form.setSourceShelfExternalId(sourceShelfExternalId);
    form.setSourceBoxExternalId(sourceBoxExternalId);
    form.setTargetSpaceExternalId(targetSpaceExternalId);
    form.setTargetShelfExternalId(targetShelfExternalId);
    form.setTargetBoxExternalId(targetBoxExternalId);
    StockMoveForm.Item moveItem = new StockMoveForm.Item();
    moveItem.setItemExternalId(ITEM_EXTERNAL_ID);
    moveItem.setCount(count);
    form.setItems(List.of(moveItem));
    return form;
  }

  private StockDTO stock(Long id) {
    StockDTO stock = new StockDTO();
    stock.setId(id);
    return stock;
  }

  private UserDTO user(Long id) {
    UserDTO dto = new UserDTO();
    dto.setId(id);
    dto.setEmail(USERNAME);
    return dto;
  }

  private ItemDTO item(Long id, UUID externalId, Long userId) {
    ItemDTO dto = new ItemDTO();
    dto.setId(id);
    dto.setExternalId(externalId);
    dto.setUserId(userId);
    dto.setActive(true);
    return dto;
  }

  private SpaceDTO space(Long id, UUID externalId, Long userId) {
    SpaceDTO dto = new SpaceDTO();
    dto.setId(id);
    dto.setExternalId(externalId);
    dto.setUserId(userId);
    return dto;
  }

  private ShelfDTO shelf(Long id, UUID externalId, Long spaceId) {
    ShelfDTO dto = new ShelfDTO();
    dto.setId(id);
    dto.setExternalId(externalId);
    dto.setSpaceId(spaceId);
    return dto;
  }

  private BoxDTO box(Long id, UUID externalId, Long shelfId) {
    BoxDTO dto = new BoxDTO();
    dto.setId(id);
    dto.setExternalId(externalId);
    dto.setShelfId(shelfId);
    return dto;
  }

  // ── createWithNewItem ─────────────────────────────────────────────────────

  @Test
  void createWithNewItem_insertsItemAndStocksAndTransactions() {
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(imageStorageService.store(null, user, null)).thenReturn(null);

    QuickStockForm form = new QuickStockForm();
    form.setName("빠른 품목");
    form.setSpaceExternalId(SPACE_EXTERNAL_ID);
    form.setCount(2);

    stockService.createWithNewItem(form, USERNAME);

    verify(itemMapper).insertItem(any());
    ArgumentCaptor<List<StockDTO>> stockCaptor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(stockCaptor.capture());
    assertThat(stockCaptor.getValue()).hasSize(2);
    verify(transactionMapper).insertTransactions(any());
  }

  @Test
  void createWithNewItem_setsPriceOnItemAndUnits() {
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(imageStorageService.store(null, user, null)).thenReturn(null);

    QuickStockForm form = new QuickStockForm();
    form.setName("빠른 품목");
    form.setSpaceExternalId(SPACE_EXTERNAL_ID);
    form.setCount(2);
    form.setPrice(new BigDecimal("9000"));

    stockService.createWithNewItem(form, USERNAME);

    ArgumentCaptor<ItemDTO> itemCaptor = ArgumentCaptor.forClass(ItemDTO.class);
    verify(itemMapper).insertItem(itemCaptor.capture());
    assertThat(itemCaptor.getValue().getPrice()).isEqualByComparingTo("9000");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDTO>> stockCaptor = ArgumentCaptor.forClass(List.class);
    verify(stockMapper).insertStocks(stockCaptor.capture());
    assertThat(stockCaptor.getValue()).allSatisfy(s -> assertThat(s.getPrice()).isNull());
  }

  @Test
  void createWithNewItem_usesDefaultMemoWhenNoneProvided() {
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(imageStorageService.store(null, user, null)).thenReturn(null);

    QuickStockForm form = new QuickStockForm();
    form.setName("빠른 품목");
    form.setSpaceExternalId(SPACE_EXTERNAL_ID);
    form.setCount(1);
    // memo 미설정 → 기본값 "빠른 등록"

    stockService.createWithNewItem(form, USERNAME);

    ArgumentCaptor<List<StockTransactionDTO>> txCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionMapper).insertTransactions(txCaptor.capture());
    assertThat(txCaptor.getValue()).hasSize(1);
    assertThat(txCaptor.getValue().get(0).getMemo()).isEqualTo("빠른 등록");
  }

  @Test
  void createWithNewItem_throwsWhenSpaceNotFound() {
    when(userMapper.findByEmail(USERNAME)).thenReturn(Optional.of(user));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.empty());
    when(imageStorageService.store(null, user, null)).thenReturn(null);

    QuickStockForm form = new QuickStockForm();
    form.setName("빠른 품목");
    form.setSpaceExternalId(SPACE_EXTERNAL_ID);
    form.setCount(1);

    assertThatThrownBy(() -> stockService.createWithNewItem(form, USERNAME))
        .isInstanceOf(NoSuchElementException.class);

    verify(stockMapper, never()).insertStocks(any());
    verify(transactionMapper, never()).insertTransactions(any());
  }

  // ── 분기 커버리지 보강: 미검증 에러/가드 경로 ───────────────────────────────

  @Test
  void findPanelPageBySpace_rejectsSpaceOwnedByAnotherUser() {
    SpaceDTO foreignSpace = space(300L, SPACE_EXTERNAL_ID, 2L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(foreignSpace));

    assertThatThrownBy(() -> stockService.findPanelPageBySpace(SPACE_EXTERNAL_ID, USERNAME, 1))
        .isInstanceOf(SecurityException.class);

    verify(stockMapper, never()).countPanelBySpaceDirectOnly(any());
  }

  @Test
  void addUnits_rejectsCountOutOfRange() {
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    StockInOutForm tooLow = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    tooLow.setCount(0);
    assertThatThrownBy(() -> stockService.addUnits(tooLow, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.countRange");

    StockInOutForm tooHigh = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    tooHigh.setCount(51); // MAX_INBOUND_COUNT = 50
    assertThatThrownBy(() -> stockService.addUnits(tooHigh, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.countRange");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void changeStatus_rejectsNullStatus() {
    assertThatThrownBy(() -> stockService.changeStatus(STOCK_EXTERNAL_ID, null, null, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.invalidStatus");

    verify(stockMapper, never()).updateStatusAndMemoIfInStock(any(), any(), any(), any());
    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void addUnits_rejectsNullOrBlankManualSerials() {
    item.setSerialMode(TrackingMode.MANUAL);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

    StockInOutForm nullText =
        stockInOutForm(SPACE_EXTERNAL_ID, null, null); // serialNumbersText == null
    assertThatThrownBy(() -> stockService.addUnits(nullText, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.required");

    StockInOutForm blankText = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    blankText.setSerialNumbersText("   ");
    assertThatThrownBy(() -> stockService.addUnits(blankText, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.required");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void addUnits_rejectsSerialsThatAlreadyExist() {
    item.setSerialMode(TrackingMode.MANUAL);
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(stockMapper.findExistingSerialNumbers(eq(item.getId()), anyList()))
        .thenReturn(List.of("SN-1"));

    StockInOutForm form = stockInOutForm(SPACE_EXTERNAL_ID, null, null);
    form.setSerialNumbersText("SN-1");

    assertThatThrownBy(() -> stockService.addUnits(form, USERNAME))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.exists");

    verify(stockMapper, never()).insertStocks(any());
  }

  @Test
  void moveUnits_rejectsWhenSomeUnitsNoLongerInStock() {
    StockDTO unit1 = stock(701L);
    StockDTO unit2 = stock(702L);
    when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));
    when(shelfMapper.findByExternalId(SHELF_EXTERNAL_ID)).thenReturn(Optional.of(shelf));
    when(boxMapper.findByExternalId(BOX_EXTERNAL_ID)).thenReturn(Optional.of(box));
    when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
    when(stockMapper.findInStockByItemAndSpace(item.getId(), space.getId()))
        .thenReturn(List.of(unit1, unit2));
    when(stockMapper.updateLocationIfInStock(
            List.of(unit1.getId(), unit2.getId()), space.getId(), shelf.getId(), box.getId()))
        .thenReturn(1); // 2개 요청했으나 1개만 갱신됨 → 상태 변경 감지

    assertThatThrownBy(
            () ->
                stockService.moveUnits(
                    stockMoveForm(
                        SPACE_EXTERNAL_ID,
                        null,
                        null,
                        SPACE_EXTERNAL_ID,
                        SHELF_EXTERNAL_ID,
                        BOX_EXTERNAL_ID,
                        2),
                    USERNAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("error.stock.statusChanged");

    verify(transactionMapper, never()).insertTransaction(any());
  }

  @Test
  void findUnitHistory_returnsHistoryForUnitScopedToUser() {
    ItemTransactionHistoryDTO entry = new ItemTransactionHistoryDTO();
    entry.setTransactionType(TransactionType.IN);
    when(transactionMapper.findHistoryByStockExternalId(STOCK_EXTERNAL_ID, user.getId()))
        .thenReturn(List.of(entry));

    List<ItemTransactionHistoryDTO> history =
        stockService.findUnitHistory(STOCK_EXTERNAL_ID, USERNAME);

    assertThat(history).containsExactly(entry);
    verify(transactionMapper).findHistoryByStockExternalId(STOCK_EXTERNAL_ID, user.getId());
  }
}
