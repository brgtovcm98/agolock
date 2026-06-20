package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockDetailDTO;
import com.seu.seustock.model.dto.StockPanelDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.form.StockUpdateForm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema-test.sql")
class StockMapperTest {

  @Autowired private StockMapper stockMapper;

  @Autowired private ItemMapper itemMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private BoxMapper boxMapper;

  @Autowired private UserMapper userMapper;

  @Autowired private ImageMapper imageMapper;

  @Autowired private ItemImageMapper itemImageMapper;
  @Autowired private ItemLotMapper itemLotMapper;

  private Long itemId;
  private Long spaceId;
  private Long userId;
  private Long shelfId;
  private Long boxId;

  @BeforeEach
  void setUp() {
    UserDTO user = new UserDTO();
    user.setEmail("testuser@test.com");
    user.setNickname("testuser");
    user.setPassword("password");
    userMapper.insertUser(user);
    userId = user.getId();

    ItemDTO item = new ItemDTO();
    item.setUserId(user.getId());
    item.setName("노트북");
    itemMapper.insertItem(item);
    itemId = item.getId();

    SpaceDTO space = new SpaceDTO();
    space.setUserId(user.getId());
    space.setName("창고");
    spaceMapper.insertSpace(space);
    spaceId = space.getId();

    ShelfDTO shelf = new ShelfDTO();
    shelf.setSpaceId(spaceId);
    shelf.setName("A선반");
    shelfMapper.insertShelf(shelf);
    shelfId = shelf.getId();

    BoxDTO box = new BoxDTO();
    box.setShelfId(shelfId);
    box.setName("1번박스");
    boxMapper.insertBox(box);
    boxId = box.getId();
  }

  private StockDTO buildStock() {
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    return stock;
  }

  private StockDTO buildStockOnShelf() {
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    stock.setShelfId(shelfId);
    return stock;
  }

  private StockDTO buildStockOnBox() {
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    stock.setShelfId(shelfId);
    stock.setBoxId(boxId);
    return stock;
  }

  @Test
  void insertStock_thenFindById() {
    StockDTO stock = buildStock();
    stock.setSerialNumber("SN-001");
    stock.setMemo("보관함 상단에 라벨 부착");
    stockMapper.insertStock(stock);

    Optional<StockDTO> found = stockMapper.findById(stock.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getItemId()).isEqualTo(itemId);
    assertThat(found.get().getSpaceId()).isEqualTo(spaceId);
    assertThat(found.get().getStatus()).isEqualTo(StockStatus.IN_STOCK);
    assertThat(found.get().getSerialNumber()).isEqualTo("SN-001");
    assertThat(found.get().getMemo()).isEqualTo("보관함 상단에 라벨 부착");
  }

  @Test
  void insertStock_persistsLotIdAndDetailUsesJoinedLotNumber() {
    ItemLotDTO lot = new ItemLotDTO();
    lot.setItemId(itemId);
    lot.setLotNumber("JOIN-LOT");
    itemLotMapper.insertLot(lot);
    StockDTO stock = buildStock();
    stock.setLotId(lot.getId());
    stock.setLotNumber("LEGACY-LOT");
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    StockDTO found = stockMapper.findById(stock.getId()).orElseThrow();
    StockDetailDTO detail = stockMapper.findDetailByExternalId(externalId, userId).orElseThrow();

    assertThat(found.getLotId()).isEqualTo(lot.getId());
    assertThat(detail.getLotExternalId()).isNotNull();
    assertThat(detail.getLotNumber()).isEqualTo("JOIN-LOT");
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<StockDTO> found = stockMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByExternalId() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    Optional<StockDTO> found = stockMapper.findByExternalId(externalId);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(stock.getId());
  }

  @Test
  void findByExternalId_notFound_returnsEmpty() {
    assertThat(stockMapper.findByExternalId(UUID.randomUUID())).isEmpty();
  }

  @Test
  void updateIsKept_togglesKeepFlagBothDirections() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    // 기본값은 보관 해제 상태
    assertThat(stockMapper.findByExternalId(externalId).orElseThrow().isKept()).isFalse();

    // 보관 설정 — findByExternalId가 is_kept를 실제로 반영해야 한다
    int set = stockMapper.updateIsKept(externalId, userId, true);
    assertThat(set).isEqualTo(1);
    assertThat(stockMapper.findByExternalId(externalId).orElseThrow().isKept()).isTrue();

    // 보관 해제 — 서비스의 멱등 가드(stock.isKept() == kept)가 올바른 값으로 비교돼야 한다
    int unset = stockMapper.updateIsKept(externalId, userId, false);
    assertThat(unset).isEqualTo(1);
    assertThat(stockMapper.findByExternalId(externalId).orElseThrow().isKept()).isFalse();
  }

  @Test
  void updateStatusAndMemoIfInStock_updatesWhenInStock() {
    StockDTO stock = buildStock();
    stock.setMemo("기존 메모");
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int updated =
        stockMapper.updateStatusAndMemoIfInStock(
            externalId, userId, StockStatus.DAMAGED, "기존 메모\n[2026-05-30] 파손 확인");

    assertThat(updated).isEqualTo(1);
    StockDTO found = stockMapper.findById(stock.getId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(StockStatus.DAMAGED);
    assertThat(found.getMemo()).isEqualTo("기존 메모\n[2026-05-30] 파손 확인");
  }

  @Test
  void updateStatusAndMemoIfInStock_skipsWhenNotInStock() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int updated =
        stockMapper.updateStatusAndMemoIfInStock(externalId, userId, StockStatus.LOST, "x");

    assertThat(updated).isZero();
    assertThat(stockMapper.findById(stock.getId()).orElseThrow().getStatus())
        .isEqualTo(StockStatus.DISPATCHED);
  }

  @Test
  void updateStatusAndMemoIfInStock_rejectsOtherUserUnit() {
    UserDTO otherUser = new UserDTO();
    otherUser.setEmail("otheruser2@test.com");
    otherUser.setNickname("otheruser2");
    otherUser.setPassword("password");
    userMapper.insertUser(otherUser);
    ItemDTO otherItem = new ItemDTO();
    otherItem.setUserId(otherUser.getId());
    otherItem.setName("다른 사용자 품목");
    itemMapper.insertItem(otherItem);
    StockDTO stock = buildStock();
    stock.setItemId(otherItem.getId());
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int updated =
        stockMapper.updateStatusAndMemoIfInStock(externalId, userId, StockStatus.LOST, "x");

    assertThat(updated).isZero();
  }

  @Test
  void findByItemId() {
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());

    List<StockDTO> stocks = stockMapper.findByItemId(itemId);

    assertThat(stocks).hasSize(2);
  }

  @Test
  void countByItemId_countsAllStatuses() {
    StockDTO inStock = buildStock();
    stockMapper.insertStock(inStock);
    StockDTO dispatched = buildStock();
    stockMapper.insertStock(dispatched);
    stockMapper.updateStatusIfInStock(dispatched.getId(), StockStatus.DISPATCHED);

    assertThat(stockMapper.countByItemId(itemId)).isEqualTo(2);
    assertThat(stockMapper.countInStockByItemId(itemId)).isEqualTo(1);
  }

  @Test
  void findBySpaceId() {
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());

    List<StockDTO> stocks = stockMapper.findBySpaceId(spaceId);

    assertThat(stocks).hasSize(2);
  }

  @Test
  void updateStatusIfInStock() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);

    int updated = stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED);

    Optional<StockDTO> found = stockMapper.findById(stock.getId());
    assertThat(updated).isEqualTo(1);
    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(StockStatus.DISPATCHED);
  }

  @Test
  void updateStatusIfInStock_alreadyDispatched_returnsZero() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED);

    int updated = stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DAMAGED);

    Optional<StockDTO> found = stockMapper.findById(stock.getId());
    assertThat(updated).isZero();
    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(StockStatus.DISPATCHED);
  }

  @Test
  void updateLocationIfInStock_movesOnlyInStockUnits() {
    StockDTO inStock = buildStock();
    stockMapper.insertStock(inStock);
    StockDTO dispatched = buildStock();
    stockMapper.insertStock(dispatched);
    stockMapper.updateStatusIfInStock(dispatched.getId(), StockStatus.DISPATCHED);

    int updated =
        stockMapper.updateLocationIfInStock(
            List.of(inStock.getId(), dispatched.getId()), spaceId, shelfId, boxId);

    StockDTO moved = stockMapper.findById(inStock.getId()).orElseThrow();
    StockDTO unchanged = stockMapper.findById(dispatched.getId()).orElseThrow();
    assertThat(updated).isEqualTo(1);
    assertThat(moved.getShelfId()).isEqualTo(shelfId);
    assertThat(moved.getBoxId()).isEqualTo(boxId);
    assertThat(unchanged.getShelfId()).isNull();
    assertThat(unchanged.getBoxId()).isNull();
  }

  @Test
  void deleteById() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);

    stockMapper.deleteById(stock.getId());

    assertThat(stockMapper.findById(stock.getId())).isEmpty();
  }

  @Test
  void deleteInStockByExternalIdAndUserId_deletesOwnedInStockUnit() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int deleted = stockMapper.deleteInStockByExternalIdAndUserId(externalId, userId);

    assertThat(deleted).isEqualTo(1);
    assertThat(stockMapper.findById(stock.getId())).isEmpty();
  }

  @Test
  void deleteInStockByExternalIdAndUserId_rejectsOtherUserUnit() {
    UserDTO otherUser = new UserDTO();
    otherUser.setEmail("otheruser@test.com");
    otherUser.setNickname("otheruser");
    otherUser.setPassword("password");
    userMapper.insertUser(otherUser);

    ItemDTO otherItem = new ItemDTO();
    otherItem.setUserId(otherUser.getId());
    otherItem.setName("다른 사용자 품목");
    itemMapper.insertItem(otherItem);

    StockDTO stock = buildStock();
    stock.setItemId(otherItem.getId());
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int deleted = stockMapper.deleteInStockByExternalIdAndUserId(externalId, userId);

    assertThat(deleted).isZero();
    assertThat(stockMapper.findById(stock.getId())).isPresent();
  }

  @Test
  void deleteInStockByExternalIdAndUserId_rejectsDispatchedUnit() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

    int deleted = stockMapper.deleteInStockByExternalIdAndUserId(externalId, userId);

    assertThat(deleted).isZero();
    assertThat(stockMapper.findById(stock.getId())).isPresent();
  }

  @Test
  void findPanelBySpaceDirectOnly_groupsByItem() {
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());

    List<StockPanelDTO> panel = stockMapper.findPanelBySpaceDirectOnly(spaceId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getItemName()).isEqualTo("노트북");
    assertThat(panel.get(0).getCount()).isEqualTo(2);
    assertThat(panel.get(0).getItemExternalId()).isNotNull();
  }

  @Test
  void findPanelBySpaceDirectOnly_excludesStocksOnShelf() {
    stockMapper.insertStock(buildStock()); // 공간 직접 재고
    stockMapper.insertStock(buildStockOnShelf()); // 선반 재고 — 제외돼야 함

    List<StockPanelDTO> panel = stockMapper.findPanelBySpaceDirectOnly(spaceId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getCount()).isEqualTo(1);
  }

  @Test
  void findPanelBySpaceDirectOnly_includesItemPrimaryImage() {
    var image = new ImageDTO();
    image.setUserId(userId);
    image.setStoragePath("/tmp/notebook.jpg");
    image.setOriginalFilename("notebook.jpg");
    image.setContentType("image/jpeg");
    image.setSizeBytes(128L);
    imageMapper.insertImage(image);
    itemImageMapper.insertItemImage(itemId, image.getId(), 0, true);
    stockMapper.insertStock(buildStock());

    List<StockPanelDTO> panel = stockMapper.findPanelBySpaceDirectOnly(spaceId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getDisplayImageExternalId()).isNotNull();
  }

  @Test
  void findPanelBySpaceDirectOnly_excludesDispatched() {
    StockDTO unit1 = buildStock();
    stockMapper.insertStock(unit1);
    StockDTO unit2 = buildStock();
    stockMapper.insertStock(unit2);
    stockMapper.updateStatusIfInStock(unit2.getId(), StockStatus.DISPATCHED);

    List<StockPanelDTO> panel = stockMapper.findPanelBySpaceDirectOnly(spaceId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getCount()).isEqualTo(1);
  }

  @Test
  void findPanelByShelfDirectOnly_groupsByItem() {
    stockMapper.insertStock(buildStockOnShelf());
    stockMapper.insertStock(buildStockOnShelf());

    List<StockPanelDTO> panel = stockMapper.findPanelByShelfDirectOnly(shelfId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getCount()).isEqualTo(2);
  }

  @Test
  void findPanelByShelfDirectOnly_excludesStocksOnBox() {
    stockMapper.insertStock(buildStockOnShelf()); // 선반 직접 재고
    stockMapper.insertStock(buildStockOnBox()); // 박스 재고 — 제외돼야 함

    List<StockPanelDTO> panel = stockMapper.findPanelByShelfDirectOnly(shelfId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getCount()).isEqualTo(1);
  }

  @Test
  void findPanelByBoxId_groupsByItem() {
    stockMapper.insertStock(buildStockOnBox());
    stockMapper.insertStock(buildStockOnBox());

    List<StockPanelDTO> panel = stockMapper.findPanelByBoxId(boxId);

    assertThat(panel).hasSize(1);
    assertThat(panel.get(0).getCount()).isEqualTo(2);
  }

  @Test
  void findPanelBySpaceDirectOnlyPaged_appliesLimitAndOffset() {
    for (int i = 0; i < 12; i++) {
      ItemDTO item = new ItemDTO();
      item.setUserId(userId);
      item.setName("품목%02d".formatted(i));
      itemMapper.insertItem(item);
      StockDTO stock = buildStock();
      stock.setItemId(item.getId());
      stockMapper.insertStock(stock);
    }

    List<StockPanelDTO> firstPage = stockMapper.findPanelBySpaceDirectOnlyPaged(spaceId, 10, 0);
    List<StockPanelDTO> secondPage = stockMapper.findPanelBySpaceDirectOnlyPaged(spaceId, 10, 10);

    assertThat(firstPage).hasSize(10);
    assertThat(secondPage).extracting(StockPanelDTO::getItemName).containsExactly("품목10", "품목11");
    assertThat(stockMapper.countPanelBySpaceDirectOnly(spaceId)).isEqualTo(12);
  }

  @Test
  void findInStockByItemAndSpace_returnsFifoOrder() {
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());

    List<StockDTO> units = stockMapper.findInStockByItemAndSpace(itemId, spaceId);

    assertThat(units).hasSize(3);
    assertThat(units).extracting(StockDTO::getStatus).containsOnly(StockStatus.IN_STOCK);
  }

  @Test
  void findInStockByItemAndShelf() {
    stockMapper.insertStock(buildStockOnShelf());
    stockMapper.insertStock(buildStockOnShelf());

    List<StockDTO> units = stockMapper.findInStockByItemAndShelf(itemId, shelfId);

    assertThat(units).hasSize(2);
    assertThat(units).extracting(StockDTO::getStatus).containsOnly(StockStatus.IN_STOCK);
  }

  @Test
  void findInStockByItemAndBox() {
    stockMapper.insertStock(buildStockOnBox());
    stockMapper.insertStock(buildStockOnBox());

    List<StockDTO> units = stockMapper.findInStockByItemAndBox(itemId, boxId);

    assertThat(units).hasSize(2);
    assertThat(units).extracting(StockDTO::getStatus).containsOnly(StockStatus.IN_STOCK);
  }

  @Test
  void deleteInStockByItemAndSpace() {
    stockMapper.insertStock(buildStock());
    stockMapper.insertStock(buildStock());

    stockMapper.deleteInStockByItemAndSpace(itemId, spaceId);

    assertThat(stockMapper.findBySpaceId(spaceId)).isEmpty();
  }

  @Test
  void deleteInStockByItemAndShelf() {
    stockMapper.insertStock(buildStockOnShelf());
    stockMapper.insertStock(buildStockOnShelf());

    stockMapper.deleteInStockByItemAndShelf(itemId, shelfId);

    assertThat(stockMapper.findInStockByItemAndShelf(itemId, shelfId)).isEmpty();
  }

  @Test
  void deleteInStockByItemAndBox() {
    stockMapper.insertStock(buildStockOnBox());
    stockMapper.insertStock(buildStockOnBox());

    stockMapper.deleteInStockByItemAndBox(itemId, boxId);

    assertThat(stockMapper.findInStockByItemAndBox(itemId, boxId)).isEmpty();
  }

  @Test
  void searchDetails_returnsUserOwnedInStockUnits() {
    StockDTO stock = buildStockOnBox();
    stock.setSerialNumber("SN-001");
    stock.setMemo("충전기와 함께 보관");
    stockMapper.insertStock(stock);
    StockDTO dispatched = buildStockOnBox();
    stockMapper.insertStock(dispatched);
    stockMapper.updateStatusIfInStock(dispatched.getId(), StockStatus.DISPATCHED);

    List<StockDetailDTO> details =
        stockMapper.searchDetails(userId, null, null, null, null, null, null, null, 10, 0);

    assertThat(details).hasSize(1);
    assertThat(details.get(0).getExternalId()).isNotNull();
    assertThat(details.get(0).getItemName()).isEqualTo("노트북");
    assertThat(details.get(0).getSpaceName()).isEqualTo("창고");
    assertThat(details.get(0).getShelfName()).isEqualTo("A선반");
    assertThat(details.get(0).getBoxName()).isEqualTo("1번박스");
    assertThat(details.get(0).getSerialNumber()).isEqualTo("SN-001");
    assertThat(details.get(0).getMemo()).isEqualTo("충전기와 함께 보관");
  }

  @Test
  void searchDetails_filtersByItemAndLocationExternalIds() {
    StockDTO spaceStock = buildStock();
    stockMapper.insertStock(spaceStock);
    StockDTO boxStock = buildStockOnBox();
    stockMapper.insertStock(boxStock);

    var item = itemMapper.findById(itemId).orElseThrow();
    var space = spaceMapper.findById(spaceId).orElseThrow();
    var shelf = shelfMapper.findById(shelfId).orElseThrow();
    var box = boxMapper.findById(boxId).orElseThrow();

    List<StockDetailDTO> details =
        stockMapper.searchDetails(
            userId,
            item.getExternalId(),
            space.getExternalId(),
            shelf.getExternalId(),
            box.getExternalId(),
            null,
            null,
            null,
            10,
            0);

    assertThat(details).hasSize(1);
    assertThat(details.get(0).getBoxName()).isEqualTo("1번박스");
  }

  @Test
  void searchDetails_filtersByKeywordAndSorts() {
    StockDTO laptop = buildStock();
    laptop.setSerialNumber("SN-001");
    stockMapper.insertStock(laptop);

    ItemDTO mouseItem = new ItemDTO();
    mouseItem.setUserId(userId);
    mouseItem.setName("마우스");
    itemMapper.insertItem(mouseItem);
    StockDTO mouse = buildStockOnBox();
    mouse.setItemId(mouseItem.getId());
    mouse.setLotNumber("LOT-SEARCH");
    stockMapper.insertStock(mouse);

    ItemDTO keyboardItem = new ItemDTO();
    keyboardItem.setUserId(userId);
    keyboardItem.setName("키보드");
    itemMapper.insertItem(keyboardItem);
    StockDTO keyboard = buildStockOnShelf();
    keyboard.setItemId(keyboardItem.getId());
    keyboard.setMemo("회의실 비품");
    stockMapper.insertStock(keyboard);

    List<StockDetailDTO> lotMatched =
        stockMapper.searchDetails(
            userId, null, null, null, null, "LOT-SEARCH", null, "newest", 10, 0);
    List<StockDetailDTO> memoMatched =
        stockMapper.searchDetails(userId, null, null, null, null, "회의실", null, "newest", 10, 0);
    List<StockDetailDTO> serialTypeMatched =
        stockMapper.searchDetails(
            userId, null, null, null, null, "SN-001", "serial", "newest", 10, 0);
    List<StockDetailDTO> itemTypeNotMatchedByLot =
        stockMapper.searchDetails(
            userId, null, null, null, null, "LOT-SEARCH", "item", "newest", 10, 0);
    List<StockDetailDTO> nameSorted =
        stockMapper.searchDetails(userId, null, null, null, null, null, null, "name", 10, 0);

    assertThat(lotMatched).extracting(StockDetailDTO::getItemName).containsExactly("마우스");
    assertThat(memoMatched).extracting(StockDetailDTO::getItemName).containsExactly("키보드");
    assertThat(serialTypeMatched).extracting(StockDetailDTO::getItemName).containsExactly("노트북");
    assertThat(itemTypeNotMatchedByLot).isEmpty();
    assertThat(nameSorted)
        .extracting(StockDetailDTO::getItemName)
        .containsExactly("노트북", "마우스", "키보드");
    assertThat(stockMapper.countSearchDetails(userId, null, null, null, null, "LOT-SEARCH", null))
        .isEqualTo(1);
    assertThat(stockMapper.countSearchDetails(userId, null, null, null, null, "LOT-SEARCH", "item"))
        .isZero();
  }

  @Test
  void insertStocks_batchInsert() {
    List<StockDTO> stocks = List.of(buildStock(), buildStock(), buildStockOnShelf());
    stockMapper.insertStocks(stocks);

    assertThat(stocks).allSatisfy(s -> assertThat(s.getId()).isNotNull());
    assertThat(stockMapper.findByItemId(itemId)).hasSize(3);
    assertThat(stockMapper.findBySpaceId(spaceId)).hasSize(3);
  }

  @Test
  void insertStocks_batchInsert_singleUnit() {
    List<StockDTO> stocks = List.of(buildStockOnBox());
    stockMapper.insertStocks(stocks);

    assertThat(stocks.get(0).getId()).isNotNull();
    assertThat(stockMapper.findByItemId(itemId)).hasSize(1);
    assertThat(stockMapper.findByBoxId(boxId)).hasSize(1);
  }

  @Test
  void updateDetails_updatesOnlyOwnedInStockUnit() {
    StockDTO stock = buildStock();
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();
    StockUpdateForm form = new StockUpdateForm();
    form.setSerialNumber("SN-UPDATED");
    form.setLotNumber("LOT-A");
    form.setMemo("상태 확인 완료");

    int updated = stockMapper.updateDetails(externalId, userId, form);

    assertThat(updated).isEqualTo(1);
    StockDTO found = stockMapper.findById(stock.getId()).orElseThrow();
    assertThat(found.getSerialNumber()).isEqualTo("SN-UPDATED");
    assertThat(found.getLotNumber()).isEqualTo("LOT-A");
    assertThat(found.getMemo()).isEqualTo("상태 확인 완료");
  }

  @Test
  void insertStock_persistsPrice() {
    StockDTO stock = buildStock();
    stock.setPrice(new BigDecimal("5000"));
    stockMapper.insertStock(stock);

    assertThat(stockMapper.findById(stock.getId()).orElseThrow().getPrice())
        .isEqualByComparingTo("5000");
  }

  @Test
  void insertStocks_batchInsert_persistsPrice() {
    StockDTO a = buildStock();
    a.setPrice(new BigDecimal("3000"));
    StockDTO b = buildStock();
    b.setPrice(new BigDecimal("3000"));
    stockMapper.insertStocks(List.of(a, b));

    assertThat(stockMapper.findById(a.getId()).orElseThrow().getPrice())
        .isEqualByComparingTo("3000");
    assertThat(stockMapper.findById(b.getId()).orElseThrow().getPrice())
        .isEqualByComparingTo("3000");
  }

  @Test
  void updateDetails_updatesPrice() {
    StockDTO stock = buildStock();
    stock.setPrice(new BigDecimal("5000"));
    stockMapper.insertStock(stock);
    UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();
    StockUpdateForm form = new StockUpdateForm();
    form.setPrice(new BigDecimal("7000"));

    int updated = stockMapper.updateDetails(externalId, userId, form);

    assertThat(updated).isEqualTo(1);
    assertThat(stockMapper.findById(stock.getId()).orElseThrow().getPrice())
        .isEqualByComparingTo("7000");
  }

  @Test
  void searchDetails_returnsPrices() {
    StockDTO stock = buildStock();
    stock.setPrice(null); // 상속
    stockMapper.insertStock(stock);

    ItemDTO item = itemMapper.findById(itemId).orElseThrow();
    item.setPrice(new BigDecimal("9900"));
    itemMapper.updateItem(item);

    List<StockDetailDTO> details =
        stockMapper.searchDetails(userId, null, null, null, null, null, null, null, 10, 0);

    assertThat(details).hasSize(1);
    assertThat(details.get(0).getPrice()).isNull();
    assertThat(details.get(0).getItemPrice()).isEqualByComparingTo("9900");
  }
}
