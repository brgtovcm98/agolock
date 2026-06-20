package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.SpaceSummaryDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
class SpaceMapperTest {

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private UserMapper userMapper;

  @Autowired private ItemMapper itemMapper;

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private BoxMapper boxMapper;

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

  private SpaceDTO buildSpace(String name) {
    SpaceDTO space = new SpaceDTO();
    space.setUserId(userId);
    space.setName(name);
    return space;
  }

  @Test
  void insertSpace_thenFindById() {
    SpaceDTO space = buildSpace("창고");
    spaceMapper.insertSpace(space);

    Optional<SpaceDTO> found = spaceMapper.findById(space.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getName()).isEqualTo("창고");
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<SpaceDTO> found = spaceMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByUserId() {
    spaceMapper.insertSpace(buildSpace("창고A"));
    spaceMapper.insertSpace(buildSpace("창고B"));

    List<SpaceDTO> spaces = spaceMapper.findByUserId(userId);

    assertThat(spaces).hasSize(2);
    assertThat(spaces).extracting(SpaceDTO::getName).containsExactlyInAnyOrder("창고A", "창고B");
  }

  @Test
  void findByUserIdWithOptions_filtersByNameAndSorts() {
    spaceMapper.insertSpace(buildSpace("창고B"));
    spaceMapper.insertSpace(buildSpace("창고A"));
    spaceMapper.insertSpace(buildSpace("매장"));

    List<SpaceDTO> searched = spaceMapper.findByUserIdWithOptions(userId, "창고", "name", 10, 0);
    List<SpaceDTO> newest = spaceMapper.findByUserIdWithOptions(userId, null, "newest", 10, 0);

    assertThat(searched).extracting(SpaceDTO::getName).containsExactly("창고A", "창고B");
    assertThat(newest).extracting(SpaceDTO::getName).containsExactly("매장", "창고A", "창고B");
    assertThat(spaceMapper.countByUserIdWithOptions(userId, "창고")).isEqualTo(2);
  }

  @Test
  void findByUserIdWithOptions_appliesLimitAndOffset() {
    for (int i = 0; i < 12; i++) {
      spaceMapper.insertSpace(buildSpace("공간%02d".formatted(i)));
    }

    List<SpaceDTO> firstPage = spaceMapper.findByUserIdWithOptions(userId, null, "name", 10, 0);
    List<SpaceDTO> secondPage = spaceMapper.findByUserIdWithOptions(userId, null, "name", 10, 10);

    assertThat(firstPage).hasSize(10);
    assertThat(secondPage).extracting(SpaceDTO::getName).containsExactly("공간10", "공간11");
    assertThat(spaceMapper.countByUserIdWithOptions(userId, null)).isEqualTo(12);
  }

  @Test
  void updateSpace() {
    SpaceDTO space = buildSpace("구창고");
    spaceMapper.insertSpace(space);
    space.setName("신창고");

    spaceMapper.updateSpace(space);

    Optional<SpaceDTO> found = spaceMapper.findById(space.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("신창고");
  }

  @Test
  void deleteById() {
    SpaceDTO space = buildSpace("삭제창고");
    spaceMapper.insertSpace(space);

    spaceMapper.deleteById(space.getId());

    assertThat(spaceMapper.findById(space.getId())).isEmpty();
  }

  // ── findSummariesBySpaceIds ─────────────────────────────────────────────────

  private Long insertSpace(String name) {
    SpaceDTO space = buildSpace(name);
    spaceMapper.insertSpace(space);
    return space.getId();
  }

  private Long insertItem(String name) {
    ItemDTO item = new ItemDTO();
    item.setUserId(userId);
    item.setName(name);
    itemMapper.insertItem(item);
    return item.getId();
  }

  private Long insertShelf(Long spaceId, String name) {
    ShelfDTO shelf = new ShelfDTO();
    shelf.setSpaceId(spaceId);
    shelf.setName(name);
    shelfMapper.insertShelf(shelf);
    return shelf.getId();
  }

  private Long insertBox(Long shelfId, String name) {
    BoxDTO box = new BoxDTO();
    box.setShelfId(shelfId);
    box.setName(name);
    boxMapper.insertBox(box);
    return box.getId();
  }

  private Long insertStock(
      Long itemId,
      Long spaceId,
      Long shelfId,
      Long boxId,
      StockStatus status,
      BigDecimal price,
      LocalDate expiration) {
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    stock.setShelfId(shelfId);
    stock.setBoxId(boxId);
    stock.setPrice(price);
    stock.setExpirationDate(expiration);
    stockMapper.insertStock(stock); // 모든 재고는 IN_STOCK으로 생성됨
    if (status != StockStatus.IN_STOCK) {
      stockMapper.updateStatusIfInStock(stock.getId(), status); // 원장 전이로 상태 변경
    }
    return stock.getId();
  }

  @Test
  void findSummariesBySpaceIds_aggregatesPerSpace() {
    LocalDate today = LocalDate.now();
    LocalDate soonCutoff = today.plusDays(7);

    Long spaceA = insertSpace("주방");
    Long itemA = insertItem("우유");
    Long itemB = insertItem("계란");

    Long shelfA1 = insertShelf(spaceA, "A1선반");
    insertShelf(spaceA, "A2선반"); // 2 shelves
    Long boxA1 = insertBox(shelfA1, "박스1");
    insertBox(shelfA1, "박스2");
    insertBox(shelfA1, "박스3"); // 3 boxes

    insertStock(itemA, spaceA, null, null, StockStatus.IN_STOCK, new BigDecimal("1000"), null);
    insertStock(itemA, spaceA, shelfA1, null, StockStatus.IN_STOCK, new BigDecimal("2000"), null);
    insertStock(itemB, spaceA, shelfA1, boxA1, StockStatus.IN_STOCK, new BigDecimal("500"), null);
    insertStock(
        itemA,
        spaceA,
        null,
        null,
        StockStatus.IN_STOCK,
        new BigDecimal("100"),
        today.plusDays(3)); // 임박
    insertStock(
        itemA,
        spaceA,
        null,
        null,
        StockStatus.IN_STOCK,
        new BigDecimal("100"),
        today.minusDays(1)); // 만료
    insertStock(itemA, spaceA, null, null, StockStatus.IN_STOCK, null, null); // null price
    insertStock(itemB, spaceA, null, null, StockStatus.DAMAGED, new BigDecimal("9999"), null);
    insertStock(itemB, spaceA, null, null, StockStatus.LOST, new BigDecimal("8888"), null);
    insertStock(
        itemA, spaceA, null, null, StockStatus.DISPATCHED, new BigDecimal("7777"), null); // 제외
    Long disposed =
        insertStock(itemA, spaceA, null, null, StockStatus.DISPOSED, new BigDecimal("6666"), null);

    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(disposed);
    tx.setTransactionType(TransactionType.IN);
    stockTransactionMapper.insertTransaction(tx);

    // 격리 확인용: 다른 공간에 IN_STOCK 2건
    Long spaceOther = insertSpace("창고");
    insertStock(itemA, spaceOther, null, null, StockStatus.IN_STOCK, new BigDecimal("10"), null);
    insertStock(itemA, spaceOther, null, null, StockStatus.IN_STOCK, new BigDecimal("20"), null);

    SpaceDTO a = spaceMapper.findById(spaceA).orElseThrow();
    SpaceDTO other = spaceMapper.findById(spaceOther).orElseThrow();

    Map<java.util.UUID, SpaceSummaryDTO> byId =
        spaceMapper.findSummariesBySpaceIds(List.of(spaceA, spaceOther), today, soonCutoff).stream()
            .collect(Collectors.toMap(SpaceSummaryDTO::getSpaceExternalId, Function.identity()));

    SpaceSummaryDTO sa = byId.get(a.getExternalId());
    assertThat(sa).isNotNull();
    assertThat(sa.getItemCount()).isEqualTo(2); // itemA, itemB (IN_STOCK)
    assertThat(sa.getStockCount()).isEqualTo(6); // 6 IN_STOCK rows
    assertThat(sa.getTotalValue()).isEqualByComparingTo("3700"); // 1000+2000+500+100+100+0
    assertThat(sa.getExpiringCount()).isEqualTo(1); // today+3
    assertThat(sa.getExpiredCount()).isEqualTo(1); // today-1
    assertThat(sa.getDamagedLostCount()).isEqualTo(2); // DAMAGED + LOST
    assertThat(sa.getShelfCount()).isEqualTo(2);
    assertThat(sa.getBoxCount()).isEqualTo(3);
    assertThat(sa.getLastActivityAt()).isNotNull();

    SpaceSummaryDTO so = byId.get(other.getExternalId()); // 격리
    assertThat(so.getStockCount()).isEqualTo(2);
    assertThat(so.getTotalValue()).isEqualByComparingTo("30");
    assertThat(so.getShelfCount()).isZero();
    assertThat(so.getLastActivityAt()).isNull();
  }

  @Test
  void findSummariesBySpaceIds_expiryWindowBoundariesInclusive() {
    LocalDate today = LocalDate.now();
    LocalDate soonCutoff = today.plusDays(7);

    Long space = insertSpace("냉장고");
    Long item = insertItem("요거트");

    insertStock(item, space, null, null, StockStatus.IN_STOCK, null, today); // 경계 하한 → 임박
    insertStock(
        item, space, null, null, StockStatus.IN_STOCK, null, today.plusDays(7)); // 경계 상한 → 임박
    insertStock(
        item, space, null, null, StockStatus.IN_STOCK, null, today.plusDays(8)); // 창 밖 → 임박 아님
    insertStock(item, space, null, null, StockStatus.IN_STOCK, null, today.minusDays(1)); // 만료

    SpaceDTO s = spaceMapper.findById(space).orElseThrow();
    SpaceSummaryDTO summary =
        spaceMapper.findSummariesBySpaceIds(List.of(space), today, soonCutoff).stream()
            .filter(x -> x.getSpaceExternalId().equals(s.getExternalId()))
            .findFirst()
            .orElseThrow();

    assertThat(summary.getExpiringCount()).isEqualTo(2); // today, today+7 (둘 다 포함)
  }

  @Test
  void findSummariesBySpaceIds_inheritedUnitsUseItemPrice() {
    LocalDate today = LocalDate.now();
    Long space = insertSpace("진열대");

    ItemDTO priced = new ItemDTO();
    priced.setUserId(userId);
    priced.setName("음료");
    priced.setPrice(new BigDecimal("1000"));
    itemMapper.insertItem(priced);

    Long free = insertItem("증정품");

    insertStock(priced.getId(), space, null, null, StockStatus.IN_STOCK, null, null);
    insertStock(
        priced.getId(), space, null, null, StockStatus.IN_STOCK, new BigDecimal("500"), null);
    insertStock(free, space, null, null, StockStatus.IN_STOCK, null, null);
    insertStock(free, space, null, null, StockStatus.IN_STOCK, new BigDecimal("300"), null);

    SpaceDTO s = spaceMapper.findById(space).orElseThrow();
    SpaceSummaryDTO summary =
        spaceMapper.findSummariesBySpaceIds(List.of(space), today, today.plusDays(7)).stream()
            .filter(x -> x.getSpaceExternalId().equals(s.getExternalId()))
            .findFirst()
            .orElseThrow();

    assertThat(summary.getStockCount()).isEqualTo(4);
    assertThat(summary.getTotalValue()).isEqualByComparingTo("1800");
  }
}
