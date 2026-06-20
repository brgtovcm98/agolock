package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.time.LocalDate;
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
class ItemLotMapperTest {

  @Autowired private ItemLotMapper itemLotMapper;
  @Autowired private ItemMapper itemMapper;
  @Autowired private StockMapper stockMapper;
  @Autowired private SpaceMapper spaceMapper;
  @Autowired private UserMapper userMapper;

  private Long userId;
  private Long itemId;
  private Long spaceId;

  @BeforeEach
  void setUp() {
    UserDTO user = new UserDTO();
    user.setEmail("lot@test.com");
    user.setNickname("lot-user");
    user.setPassword("password");
    userMapper.insertUser(user);
    userId = user.getId();

    ItemDTO item = new ItemDTO();
    item.setUserId(userId);
    item.setName("시약");
    itemMapper.insertItem(item);
    itemId = item.getId();

    SpaceDTO space = new SpaceDTO();
    space.setUserId(userId);
    space.setName("창고");
    spaceMapper.insertSpace(space);
    spaceId = space.getId();
  }

  @Test
  void insertLot_thenFindByItemIdAndLotNumber() {
    ItemLotDTO lot = lot("LOT-001");
    itemLotMapper.insertLot(lot);

    ItemLotDTO found = itemLotMapper.findByItemIdAndLotNumber(itemId, "LOT-001").orElseThrow();

    assertThat(found.getId()).isEqualTo(lot.getId());
    assertThat(found.getExternalId()).isNotNull();
    assertThat(found.getExpirationDate()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void findUnitsByLotExternalId_returnsUnitsOwnedByUser() {
    ItemLotDTO lot = lot("LOT-002");
    itemLotMapper.insertLot(lot);
    StockDTO stock = new StockDTO();
    stock.setItemId(itemId);
    stock.setSpaceId(spaceId);
    stock.setLotId(lot.getId());
    stock.setSerialNumber("SN-LOT-1");
    stockMapper.insertStock(stock);
    ItemLotDTO persisted = itemLotMapper.findById(lot.getId()).orElseThrow();

    var units = itemLotMapper.findUnitsByLotExternalId(persisted.getExternalId(), userId);

    assertThat(units).hasSize(1);
    assertThat(units.get(0).getSerialNumber()).isEqualTo("SN-LOT-1");
    assertThat(units.get(0).getSpaceName()).isEqualTo("창고");
  }

  private ItemLotDTO lot(String lotNumber) {
    ItemLotDTO lot = new ItemLotDTO();
    lot.setItemId(itemId);
    lot.setLotNumber(lotNumber);
    lot.setExpirationDate(LocalDate.of(2026, 12, 31));
    return lot;
  }
}
