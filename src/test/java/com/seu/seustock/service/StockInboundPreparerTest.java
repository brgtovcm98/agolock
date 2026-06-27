package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seu.seustock.mapper.ItemLotMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.enumeration.TrackingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class StockInboundPreparerTest {

  @Mock private StockMapper stockMapper;
  @Mock private ItemMapper itemMapper;
  @Mock private ItemLotMapper itemLotMapper;
  @Mock private MessageSource messageSource;
  @Spy private SerialNumberGenerator serialNumberGenerator = new SerialNumberGenerator();
  @Spy private LotNumberGenerator lotNumberGenerator = new LotNumberGenerator();

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneId.systemDefault());

  private StockInboundPreparer preparer;
  private ItemDTO item;
  private VerifiedStockLocation location;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    item = new ItemDTO();
    item.setId(10L);
    item.setName("Item");
    item.setActive(true);

    SpaceDTO space = new SpaceDTO();
    space.setId(100L);
    location = new VerifiedStockLocation(space, null, null);

    preparer =
        new StockInboundPreparer(
            stockMapper,
            itemMapper,
            itemLotMapper,
            serialNumberGenerator,
            lotNumberGenerator,
            clock,
            messageSource);
  }

  @Test
  void prepareInboundUnits_usesManualSerialCountAndReusesManualLot() {
    item.setSerialMode(TrackingMode.MANUAL);
    item.setLotMode(TrackingMode.MANUAL);
    ItemLotDTO lot = new ItemLotDTO();
    lot.setId(50L);
    lot.setItemId(item.getId());
    lot.setLotNumber("LOT-1");
    lot.setExpirationDate(LocalDate.of(2026, 7, 1));
    when(itemLotMapper.findByItemIdAndLotNumber(item.getId(), "LOT-1"))
        .thenReturn(Optional.of(lot));

    List<StockDTO> units =
        preparer.prepareInboundUnits(
            item,
            location,
            new StockInboundSpec(1, null, "S-1\nS-2", "LOT-1", null, null, "memo"));

    assertThat(units).hasSize(2);
    assertThat(units).extracting(StockDTO::getSerialNumber).containsExactly("S-1", "S-2");
    assertThat(units).extracting(StockDTO::getLotId).containsOnly(lot.getId());
    assertThat(units).extracting(StockDTO::getLotNumber).containsOnly(lot.getLotNumber());
    verify(itemLotMapper, never()).insertLot(any());
    verify(stockMapper).insertStocks(units);
  }

  @Test
  void prepareInboundUnits_generatesAutoSerialsAndLot() {
    item.setSerialMode(TrackingMode.AUTO);
    item.setSerialPrefix("SN");
    item.setSerialPaddingLength(3);
    item.setSerialIncrementUnit(1);
    item.setSerialNextSequence(7);
    item.setLotMode(TrackingMode.AUTO);
    item.setLotVendorCode("VD");
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(true);
    item.setLotNextSequence(3);
    item.setExpirationPeriodDays(10);
    when(itemLotMapper.findByItemIdAndLotNumber(item.getId(), "VD20260531-001"))
        .thenReturn(Optional.empty());

    List<StockDTO> units =
        preparer.prepareInboundUnits(
            item, location, new StockInboundSpec(2, null, null, null, null, null, null));

    assertThat(units).hasSize(2);
    assertThat(units).extracting(StockDTO::getSerialNumber).containsExactly("SN008", "SN009");
    assertThat(units).extracting(StockDTO::getLotNumber).containsOnly("VD20260531-001");
    assertThat(units)
        .extracting(StockDTO::getExpirationDate)
        .containsOnly(LocalDate.of(2026, 6, 10));
    verify(itemMapper).updateSerialNextSequence(item.getId(), 9);
    verify(itemMapper).updateLotSequence(item.getId(), "20260531", 1);
    verify(itemLotMapper).insertLot(any());
  }

  @Test
  void prepareInboundUnits_rejectsDuplicateManualSerials() {
    item.setSerialMode(TrackingMode.MANUAL);

    assertThatThrownBy(
            () ->
                preparer.prepareInboundUnits(
                    item,
                    location,
                    new StockInboundSpec(1, null, "S-1\nS-1", null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.duplicate");
  }

  @Test
  void prepareInboundUnits_rejectsExistingSerials() {
    item.setSerialMode(TrackingMode.MANUAL);
    when(stockMapper.findExistingSerialNumbers(item.getId(), List.of("S-1")))
        .thenReturn(List.of("S-1"));

    assertThatThrownBy(
            () ->
                preparer.prepareInboundUnits(
                    item,
                    location,
                    new StockInboundSpec(1, null, "S-1", null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.serial.exists");
  }

  @Test
  void prepareInboundUnits_rejectsCountOutOfRange() {
    assertThatThrownBy(
            () ->
                preparer.prepareInboundUnits(
                    item,
                    location,
                    new StockInboundSpec(51, null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("error.stock.countRange");
  }
}
