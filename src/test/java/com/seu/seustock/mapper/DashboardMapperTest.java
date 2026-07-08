package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.DashboardActivityDTO;
import com.seu.seustock.model.dto.DashboardSummaryDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class DashboardMapperTest {

  @Autowired private DashboardMapper dashboardMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private ItemMapper itemMapper;
  @Autowired private SpaceMapper spaceMapper;
  @Autowired private StockMapper stockMapper;
  @Autowired private StockTransactionMapper stockTransactionMapper;

  private Long userId;
  private Long itemId;
  private Long spaceId;

  private final LocalDate today = LocalDate.now();
  private final LocalDate soonCutoff = today.plusDays(7);

  @BeforeEach
  void setUp() {
    UserDTO user = new UserDTO();
    user.setEmail("dash@test.com");
    user.setNickname("dash");
    user.setPassword("password");
    userMapper.insertUser(user);
    userId = user.getId();

    ItemDTO item = new ItemDTO();
    item.setUserId(userId);
    item.setName("생수");
    item.setPrice(new BigDecimal("1000"));
    itemMapper.insertItem(item);
    itemId = item.getId();

    SpaceDTO space = new SpaceDTO();
    space.setUserId(userId);
    space.setName("창고");
    spaceMapper.insertSpace(space);
    spaceId = space.getId();
  }

  /** IN_STOCK 재고를 하나 삽입하고 id를 반환한다. */
  private Long insertInStock(BigDecimal price, LocalDate expiration) {
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    stock.setPrice(price);
    stock.setExpirationDate(expiration);
    stockMapper.insertStock(stock);
    return stock.getId();
  }

  private Long insertWithStatus(StockStatus status) {
    Long id = insertInStock(null, null);
    stockMapper.updateStatusIfInStock(id, status);
    return id;
  }

  @Test
  void findSummary_emptyUser_returnsZeros() {
    DashboardSummaryDTO summary = dashboardMapper.findSummaryByUserId(userId, today, soonCutoff);

    assertThat(summary).isNotNull();
    assertThat(summary.getTotalItemCount()).isZero();
    assertThat(summary.getTotalStockCount()).isZero();
    assertThat(summary.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.getSpaceCount()).isEqualTo(1);
    assertThat(summary.getTargetTotalStock()).isNull();
  }

  @Test
  void findSummary_aggregatesCountsValueAndStatuses() {
    // IN_STOCK: 상속가(1000) x3, 개별가(500) x1  => 총재고 4, 평가액 3500
    insertInStock(null, null);
    insertInStock(null, null);
    Long expiring = insertInStock(null, today.plusDays(3)); // 임박
    insertInStock(new BigDecimal("500"), null);

    // 만료(IN_STOCK, 과거 유통기한)
    insertInStock(null, today.minusDays(1));

    // 보관중(kept)
    Long keptId = insertInStock(null, null);
    UUID keptExternalId = stockMapper.findById(keptId).orElseThrow().getExternalId();
    stockMapper.updateIsKept(keptExternalId, userId, true);

    // 상태 전이
    insertWithStatus(StockStatus.DISPATCHED);
    insertWithStatus(StockStatus.LOST);
    insertWithStatus(StockStatus.DAMAGED);
    insertWithStatus(StockStatus.DISPOSED);

    DashboardSummaryDTO summary = dashboardMapper.findSummaryByUserId(userId, today, soonCutoff);

    // IN_STOCK 총 6개: 3(상속) + 1(개별500) + 1(만료) + 1(보관)
    assertThat(summary.getTotalStockCount()).isEqualTo(6);
    assertThat(summary.getTotalItemCount()).isEqualTo(1);
    // 평가액: 5 x 1000 + 1 x 500 = 5500 (IN_STOCK 만 합산)
    assertThat(summary.getTotalValue()).isEqualByComparingTo(new BigDecimal("5500"));
    assertThat(summary.getKeptCount()).isEqualTo(1);
    assertThat(summary.getExpiringCount()).isEqualTo(1);
    assertThat(summary.getExpiredCount()).isEqualTo(1);
    assertThat(summary.getDispatchedCount()).isEqualTo(1);
    assertThat(summary.getLostCount()).isEqualTo(1);
    assertThat(summary.getDamagedCount()).isEqualTo(1);
    assertThat(summary.getDisposedCount()).isEqualTo(1);
    assertThat(summary.getSpaceCount()).isEqualTo(1);
    assertThat(expiring).isNotNull();
  }

  @Test
  void findSummary_reflectsTargetTotalStock() {
    assertThat(dashboardMapper.findSummaryByUserId(userId, today, soonCutoff).getTargetTotalStock())
        .isNull();

    userMapper.updateTargetTotalStock(userId, 100);

    assertThat(dashboardMapper.findSummaryByUserId(userId, today, soonCutoff).getTargetTotalStock())
        .isEqualTo(100);
  }

  @Test
  void findRecentActivity_returnsOwnedTransactionsNewestFirstWithItemName() {
    Long stockId = insertInStock(null, null);
    StockTransactionDTO in = new StockTransactionDTO();
    in.setStockId(stockId);
    in.setTransactionType(TransactionType.IN);
    in.setMemo("구매 입고");
    stockTransactionMapper.insertTransaction(in);

    StockTransactionDTO adjust = new StockTransactionDTO();
    adjust.setStockId(stockId);
    adjust.setTransactionType(TransactionType.ADJUST);
    adjust.setMemo("수량 조정");
    stockTransactionMapper.insertTransaction(adjust);

    List<DashboardActivityDTO> activity = dashboardMapper.findRecentActivity(userId, 10);

    assertThat(activity).hasSize(2);
    assertThat(activity.get(0).getTransactionType()).isEqualTo(TransactionType.ADJUST);
    assertThat(activity.get(0).getItemName()).isEqualTo("생수");
    assertThat(activity.get(0).getSpaceName()).isEqualTo("창고");
    assertThat(activity.get(1).getTransactionType()).isEqualTo(TransactionType.IN);
  }

  @Test
  void findRecentActivity_respectsLimit() {
    Long stockId = insertInStock(null, null);
    for (int i = 0; i < 5; i++) {
      StockTransactionDTO tx = new StockTransactionDTO();
      tx.setStockId(stockId);
      tx.setTransactionType(TransactionType.ADJUST);
      stockTransactionMapper.insertTransaction(tx);
    }

    assertThat(dashboardMapper.findRecentActivity(userId, 3)).hasSize(3);
  }
}
