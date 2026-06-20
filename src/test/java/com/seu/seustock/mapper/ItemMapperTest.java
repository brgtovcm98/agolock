package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.TrackingMode;
import com.seu.seustock.model.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
class ItemMapperTest {

  @Autowired private ItemMapper itemMapper;

  @Autowired private UserMapper userMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private StockMapper stockMapper;

  @Autowired private StockTransactionMapper stockTransactionMapper;

  private Long userId;

  @BeforeEach
  void setUp() {
    UserDTO user = new UserDTO();
    user.setEmail("testuser@test.com");
    user.setNickname("testuser");
    user.setPassword("password");
    userMapper.insertUser(user);
    userId = user.getId();
  }

  private ItemDTO buildItem(String name, String description) {
    ItemDTO item = new ItemDTO();
    item.setUserId(userId);
    item.setName(name);
    item.setDescription(description);
    return item;
  }

  @Test
  void insertItem_thenFindById() {
    ItemDTO item = buildItem("노트북", "업무용 노트북");
    itemMapper.insertItem(item);

    Optional<ItemDTO> found = itemMapper.findById(item.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getName()).isEqualTo("노트북");
    assertThat(found.get().getDescription()).isEqualTo("업무용 노트북");
    assertThat(found.get().getUserId()).isEqualTo(userId);
    assertThat(found.get().isActive()).isTrue();
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<ItemDTO> found = itemMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByExternalId_includesStockSpaceAndHistoryCounts() {
    ItemDTO item = buildItem("노트북", null);
    itemMapper.insertItem(item);
    SpaceDTO space = new SpaceDTO();
    space.setUserId(userId);
    space.setName("창고");
    spaceMapper.insertSpace(space);
    StockDTO stock = new StockDTO();
    stock.setItemId(item.getId());
    stock.setSpaceId(space.getId());
    stockMapper.insertStock(stock);
    StockTransactionDTO transaction = new StockTransactionDTO();
    transaction.setStockId(stock.getId());
    transaction.setTransactionType(TransactionType.IN);
    stockTransactionMapper.insertTransaction(transaction);
    ItemDTO persisted = itemMapper.findById(item.getId()).orElseThrow();

    Optional<ItemDTO> found = itemMapper.findByExternalId(persisted.getExternalId());

    assertThat(found).isPresent();
    assertThat(found.get().getStockCount()).isEqualTo(1);
    assertThat(found.get().getSpaceCount()).isEqualTo(1);
    assertThat(found.get().getHistoryCount()).isEqualTo(1);
  }

  @Test
  void findByUserId() {
    itemMapper.insertItem(buildItem("노트북", null));
    itemMapper.insertItem(buildItem("마우스", null));

    List<ItemDTO> items = itemMapper.findByUserId(userId);

    assertThat(items).hasSize(2);
    assertThat(items).extracting(ItemDTO::getName).containsExactlyInAnyOrder("노트북", "마우스");
  }

  @Test
  void findByUserIdWithOptions_filtersByNameAndSorts() {
    itemMapper.insertItem(buildItem("노트북", null));
    itemMapper.insertItem(buildItem("무선 마우스", null));
    itemMapper.insertItem(buildItem("유선 마우스", null));

    List<ItemDTO> searched =
        itemMapper.findByUserIdWithOptions(userId, "마우스", "name", "name", 10, 0);
    List<ItemDTO> oldest =
        itemMapper.findByUserIdWithOptions(userId, null, "name", "oldest", 10, 0);

    assertThat(searched).extracting(ItemDTO::getName).containsExactly("무선 마우스", "유선 마우스");
    assertThat(oldest).extracting(ItemDTO::getName).containsExactly("노트북", "무선 마우스", "유선 마우스");
    assertThat(itemMapper.countByUserIdWithOptions(userId, "마우스", "name")).isEqualTo(2);
  }

  @Test
  void findByUserIdWithOptions_filtersByDescription() {
    itemMapper.insertItem(buildItem("노트북", "휴대용 업무 장비"));
    itemMapper.insertItem(buildItem("마우스", "무선 입력 장비"));
    itemMapper.insertItem(buildItem("키보드", "유선 입력 장비"));

    List<ItemDTO> searched =
        itemMapper.findByUserIdWithOptions(userId, "입력", "description", "name", 10, 0);

    assertThat(searched).extracting(ItemDTO::getName).containsExactly("마우스", "키보드");
    assertThat(itemMapper.countByUserIdWithOptions(userId, "입력", "description")).isEqualTo(2);
  }

  @Test
  void findByUserIdWithOptions_appliesLimitAndOffset() {
    for (int i = 0; i < 12; i++) {
      itemMapper.insertItem(buildItem("품목%02d".formatted(i), null));
    }

    List<ItemDTO> firstPage =
        itemMapper.findByUserIdWithOptions(userId, null, "name", "name", 10, 0);
    List<ItemDTO> secondPage =
        itemMapper.findByUserIdWithOptions(userId, null, "name", "name", 10, 10);

    assertThat(firstPage).hasSize(10);
    assertThat(secondPage).extracting(ItemDTO::getName).containsExactly("품목10", "품목11");
    assertThat(itemMapper.countByUserIdWithOptions(userId, null, "name")).isEqualTo(12);
  }

  @Test
  void deactivateById_excludesItemFromUserList() {
    ItemDTO activeItem = buildItem("활성아이템", null);
    itemMapper.insertItem(activeItem);
    ItemDTO inactiveItem = buildItem("비활성아이템", null);
    itemMapper.insertItem(inactiveItem);

    itemMapper.deactivateById(inactiveItem.getId());

    Optional<ItemDTO> found = itemMapper.findById(inactiveItem.getId());
    List<ItemDTO> items = itemMapper.findByUserId(userId);

    assertThat(found).isPresent();
    assertThat(found.get().isActive()).isFalse();
    assertThat(items).extracting(ItemDTO::getName).containsExactly("활성아이템");
  }

  @Test
  void updateItem() {
    ItemDTO item = buildItem("구형노트북", "오래된 노트북");
    itemMapper.insertItem(item);
    item.setName("신형노트북");
    item.setDescription("최신 노트북");

    itemMapper.updateItem(item);

    Optional<ItemDTO> found = itemMapper.findById(item.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("신형노트북");
    assertThat(found.get().getDescription()).isEqualTo("최신 노트북");
  }

  @Test
  void insertItem_persistsPrice() {
    ItemDTO item = buildItem("노트북", "업무용");
    item.setPrice(new BigDecimal("1500000"));
    itemMapper.insertItem(item);

    ItemDTO found = itemMapper.findById(item.getId()).orElseThrow();
    assertThat(found.getPrice()).isEqualByComparingTo("1500000");
  }

  @Test
  void insertItem_nullPrice_persistsNull() {
    ItemDTO item = buildItem("사은품", null);
    itemMapper.insertItem(item);

    assertThat(itemMapper.findById(item.getId()).orElseThrow().getPrice()).isNull();
  }

  @Test
  void updateItem_changesPrice() {
    ItemDTO item = buildItem("노트북", null);
    item.setPrice(new BigDecimal("1000000"));
    itemMapper.insertItem(item);
    item.setPrice(new BigDecimal("1200000"));

    itemMapper.updateItem(item);

    assertThat(itemMapper.findById(item.getId()).orElseThrow().getPrice())
        .isEqualByComparingTo("1200000");
  }

  @Test
  void insertAndUpdateItem_persistsSerialLotPolicyColumns() {
    ItemDTO item = buildItem("정책 품목", null);
    item.setSerialMode(TrackingMode.AUTO);
    item.setSerialPrefix("SEU-");
    item.setSerialPaddingLength(4);
    item.setSerialIncrementUnit(2);
    item.setSerialNextSequence(10);
    item.setLotMode(TrackingMode.MANUAL);
    item.setLotVendorCode("VN-");
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(true);
    item.setLotNextSequence(3);
    item.setExpirationPeriodDays(90);
    itemMapper.insertItem(item);

    ItemDTO found = itemMapper.findById(item.getId()).orElseThrow();
    assertThat(found.getSerialMode()).isEqualTo(TrackingMode.AUTO);
    assertThat(found.getSerialPrefix()).isEqualTo("SEU-");
    assertThat(found.getSerialPaddingLength()).isEqualTo(4);
    assertThat(found.getSerialIncrementUnit()).isEqualTo(2);
    assertThat(found.getSerialNextSequence()).isEqualTo(10);
    assertThat(found.getLotMode()).isEqualTo(TrackingMode.MANUAL);
    assertThat(found.getLotVendorCode()).isEqualTo("VN-");
    assertThat(found.getExpirationPeriodDays()).isEqualTo(90);

    itemMapper.updateSerialNextSequence(item.getId(), 14);
    itemMapper.updateLotSequence(item.getId(), "20260531", 4);

    ItemDTO updated = itemMapper.findById(item.getId()).orElseThrow();
    assertThat(updated.getSerialNextSequence()).isEqualTo(14);
    assertThat(updated.getLotSequenceKey()).isEqualTo("20260531");
    assertThat(updated.getLotNextSequence()).isEqualTo(4);
  }

  @Test
  void deleteById() {
    ItemDTO item = buildItem("삭제아이템", null);
    itemMapper.insertItem(item);

    itemMapper.deleteById(item.getId());

    assertThat(itemMapper.findById(item.getId())).isEmpty();
  }
}
