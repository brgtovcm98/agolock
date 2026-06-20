package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemTransactionHistoryDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.TransactionType;
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
class StockTransactionMapperTest {

  @Autowired private StockTransactionMapper stockTransactionMapper;

  @Autowired private StockMapper stockMapper;

  @Autowired private ItemMapper itemMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private BoxMapper boxMapper;

  @Autowired private UserMapper userMapper;

  private Long stockId;
  private Long spaceId;
  private Long shelfId;
  private Long boxId;
  private Long userId;

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

    StockDTO stock = new StockDTO();
    stock.setItemId(item.getId());
    stock.setSpaceId(spaceId);
    stockMapper.insertStock(stock);
    stockId = stock.getId();
  }

  private StockTransactionDTO buildTransaction(TransactionType type, String memo) {
    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(stockId);
    tx.setTransactionType(type);
    tx.setMemo(memo);
    return tx;
  }

  @Test
  void insertTransaction_thenFindById() {
    StockTransactionDTO tx = buildTransaction(TransactionType.IN, "입고");
    stockTransactionMapper.insertTransaction(tx);

    Optional<StockTransactionDTO> found = stockTransactionMapper.findById(tx.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getStockId()).isEqualTo(stockId);
    assertThat(found.get().getTransactionType()).isEqualTo(TransactionType.IN);
    assertThat(found.get().getMemo()).isEqualTo("입고");
    assertThat(found.get().getFromSpaceId()).isNull();
    assertThat(found.get().getToSpaceId()).isNull();
  }

  @Test
  void insertMoveTransaction_persistsLocationSnapshot() {
    StockTransactionDTO tx = buildTransaction(TransactionType.MOVE, "정리");
    tx.setFromSpaceId(spaceId);
    tx.setToSpaceId(spaceId);
    tx.setToShelfId(shelfId);
    tx.setToBoxId(boxId);

    stockTransactionMapper.insertTransaction(tx);

    StockTransactionDTO found = stockTransactionMapper.findById(tx.getId()).orElseThrow();
    assertThat(found.getTransactionType()).isEqualTo(TransactionType.MOVE);
    assertThat(found.getFromSpaceId()).isEqualTo(spaceId);
    assertThat(found.getFromShelfId()).isNull();
    assertThat(found.getFromBoxId()).isNull();
    assertThat(found.getToSpaceId()).isEqualTo(spaceId);
    assertThat(found.getToShelfId()).isEqualTo(shelfId);
    assertThat(found.getToBoxId()).isEqualTo(boxId);
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<StockTransactionDTO> found = stockTransactionMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByStockId_returnsAllTransactions() {
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "입고"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.OUT, "출고"));

    List<StockTransactionDTO> txList = stockTransactionMapper.findByStockId(stockId);

    assertThat(txList).hasSize(2);
    assertThat(txList)
        .extracting(StockTransactionDTO::getTransactionType)
        .containsExactly(TransactionType.IN, TransactionType.OUT);
  }

  @Test
  void findByStockId_noTransactions_returnsEmpty() {
    List<StockTransactionDTO> txList = stockTransactionMapper.findByStockId(stockId);
    assertThat(txList).isEmpty();
  }

  @Test
  void insertTransactions_batchInsert() {
    StockDTO stock2 = new StockDTO();
    stock2.setItemId(stockMapper.findById(stockId).orElseThrow().getItemId());
    stock2.setSpaceId(spaceId);
    stockMapper.insertStock(stock2);

    List<StockTransactionDTO> txs =
        List.of(
            buildTransaction(TransactionType.IN, "일괄 입고 1"),
            buildTransaction(TransactionType.IN, "일괄 입고 2"));
    txs.get(1).setStockId(stock2.getId());
    stockTransactionMapper.insertTransactions(txs);

    assertThat(stockTransactionMapper.findByStockId(stockId)).hasSize(1);
    assertThat(stockTransactionMapper.findByStockId(stock2.getId())).hasSize(1);
  }

  @Test
  void insertTransactions_batchInsert_singleTransaction() {
    List<StockTransactionDTO> txs = List.of(buildTransaction(TransactionType.OUT, "단건 배치"));
    stockTransactionMapper.insertTransactions(txs);

    assertThat(stockTransactionMapper.findByStockId(stockId)).hasSize(1);
    assertThat(stockTransactionMapper.findByStockId(stockId).get(0).getMemo()).isEqualTo("단건 배치");
  }

  @Test
  void findFrequentMemosByUserIdAndType_returnsMostUsedMemos() {
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "구매 입고"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "구매 입고"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "반품 입고"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.OUT, "사용 출고"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "초기 등록"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "빠른 등록"));
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, " "));

    List<String> memos =
        stockTransactionMapper.findFrequentMemosByUserIdAndType(userId, TransactionType.IN, 2);

    assertThat(memos).containsExactly("구매 입고", "반품 입고");
  }

  @Test
  void findHistoryByStockExternalId_returnsChronologicalHistoryWithLocations() {
    UUID stockExternalId = stockMapper.findById(stockId).orElseThrow().getExternalId();

    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "입고"));
    StockTransactionDTO move = buildTransaction(TransactionType.MOVE, "정리");
    move.setFromSpaceId(spaceId);
    move.setToSpaceId(spaceId);
    move.setToShelfId(shelfId);
    move.setToBoxId(boxId);
    stockTransactionMapper.insertTransaction(move);
    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.OUT, "출고"));

    List<ItemTransactionHistoryDTO> history =
        stockTransactionMapper.findHistoryByStockExternalId(stockExternalId, userId);

    assertThat(history)
        .extracting(ItemTransactionHistoryDTO::getTransactionType)
        .containsExactly(TransactionType.IN, TransactionType.MOVE, TransactionType.OUT);
    assertThat(history.get(0).getSpaceName()).isEqualTo("창고");

    ItemTransactionHistoryDTO moveEntry = history.get(1);
    assertThat(moveEntry.getFromSpaceName()).isEqualTo("창고");
    assertThat(moveEntry.getToSpaceName()).isEqualTo("창고");
    assertThat(moveEntry.getToShelfName()).isEqualTo("A선반");
    assertThat(moveEntry.getToBoxName()).isEqualTo("1번박스");
  }

  @Test
  void findHistoryByStockExternalId_excludesOtherStocksAndOtherUsers() {
    UUID stockExternalId = stockMapper.findById(stockId).orElseThrow().getExternalId();
    Long itemId = stockMapper.findById(stockId).orElseThrow().getItemId();

    StockDTO other = new StockDTO();
    other.setItemId(itemId);
    other.setSpaceId(spaceId);
    stockMapper.insertStock(other);
    StockTransactionDTO otherTx = new StockTransactionDTO();
    otherTx.setStockId(other.getId());
    otherTx.setTransactionType(TransactionType.IN);
    otherTx.setMemo("다른 단위 입고");
    stockTransactionMapper.insertTransaction(otherTx);

    stockTransactionMapper.insertTransaction(buildTransaction(TransactionType.IN, "내 단위 입고"));

    assertThat(stockTransactionMapper.findHistoryByStockExternalId(stockExternalId, userId))
        .extracting(ItemTransactionHistoryDTO::getMemo)
        .containsExactly("내 단위 입고");

    assertThat(stockTransactionMapper.findHistoryByStockExternalId(stockExternalId, 999999L))
        .isEmpty();
  }
}
