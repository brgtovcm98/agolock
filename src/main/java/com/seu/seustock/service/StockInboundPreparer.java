package com.seu.seustock.service;

import com.seu.seustock.mapper.ItemLotMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.enumeration.TrackingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockInboundPreparer {

  private static final int MAX_INBOUND_COUNT = 50;

  private final StockMapper stockMapper;
  private final ItemMapper itemMapper;
  private final ItemLotMapper itemLotMapper;
  private final SerialNumberGenerator serialNumberGenerator;
  private final LotNumberGenerator lotNumberGenerator;
  private final Clock clock;
  private final MessageSource messageSource;

  private record LotResolution(Long lotId, String lotNumber, LocalDate expirationDate) {}

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  List<StockDTO> prepareInboundUnits(
      ItemDTO item, VerifiedStockLocation location, StockInboundSpec spec) {
    List<String> serialNumbers = resolveSerialNumbers(item, spec);
    TrackingMode serialMode =
        item.getSerialMode() == null ? TrackingMode.NONE : item.getSerialMode();
    int count = serialMode == TrackingMode.MANUAL ? serialNumbers.size() : spec.count();
    validateInboundCount(count);
    LotResolution lot = resolveLot(item, spec);
    List<StockDTO> units = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      StockDTO unit = new StockDTO();
      unit.setItemId(item.getId());
      unit.setSpaceId(location.space().getId());
      unit.setShelfId(location.shelfId());
      unit.setBoxId(location.boxId());
      unit.setLotId(lot.lotId());
      unit.setLotNumber(lot.lotNumber());
      unit.setExpirationDate(lot.expirationDate());
      unit.setSerialNumber(serialNumbers.get(i));
      unit.setPrice(spec.price());
      unit.setMemo(spec.memo());
      units.add(unit);
    }
    stockMapper.insertStocks(units);
    return units;
  }

  private List<String> resolveSerialNumbers(ItemDTO item, StockInboundSpec spec) {
    TrackingMode mode = item.getSerialMode() == null ? TrackingMode.NONE : item.getSerialMode();
    if (mode == TrackingMode.NONE) {
      List<String> serialNumbers = new ArrayList<>(spec.count());
      String singleSerial = blankToNull(spec.serialNumber());
      for (int i = 0; i < spec.count(); i++) {
        serialNumbers.add(spec.count() == 1 ? singleSerial : null);
      }
      if (singleSerial != null) {
        rejectExistingSerials(item.getId(), List.of(singleSerial));
      }
      return serialNumbers;
    }
    if (mode == TrackingMode.MANUAL) {
      List<String> serialNumbers = parseManualSerials(spec.serialNumbersText());
      validateInboundCount(serialNumbers.size());
      rejectDuplicateSerials(serialNumbers);
      rejectExistingSerials(item.getId(), serialNumbers);
      return serialNumbers;
    }
    validateInboundCount(spec.count());
    SerialNumberGenerator.Result result =
        serialNumberGenerator.generate(
            item.getSerialPrefix(),
            item.getSerialPaddingLength(),
            item.getSerialIncrementUnit(),
            item.getSerialNextSequence(),
            spec.count());
    rejectExistingSerials(item.getId(), result.serialNumbers());
    itemMapper.updateSerialNextSequence(item.getId(), result.nextSequence());
    return result.serialNumbers();
  }

  private LotResolution resolveLot(ItemDTO item, StockInboundSpec spec) {
    TrackingMode mode = item.getLotMode() == null ? TrackingMode.NONE : item.getLotMode();
    if (mode == TrackingMode.NONE) {
      String legacyLotNumber = blankToNull(spec.lotNumber());
      LocalDate expirationDate = resolveExpirationDate(item, spec.expirationDate());
      return new LotResolution(null, legacyLotNumber, expirationDate);
    }
    String lotNumber;
    if (mode == TrackingMode.AUTO) {
      LotNumberGenerator.Result generated = lotNumberGenerator.generate(item, LocalDate.now(clock));
      lotNumber = generated.lotNumber();
      itemMapper.updateLotSequence(item.getId(), generated.sequenceKey(), generated.nextSequence());
    } else {
      lotNumber = blankToNull(spec.lotNumber());
      if (lotNumber == null) {
        throw new IllegalArgumentException(getMsg("error.lot.numberRequired"));
      }
    }
    ItemLotDTO lot =
        itemLotMapper
            .findByItemIdAndLotNumber(item.getId(), lotNumber)
            .orElseGet(() -> createLot(item, lotNumber, spec.expirationDate()));
    return new LotResolution(lot.getId(), lot.getLotNumber(), lot.getExpirationDate());
  }

  private ItemLotDTO createLot(ItemDTO item, String lotNumber, LocalDate formExpirationDate) {
    ItemLotDTO lot = new ItemLotDTO();
    lot.setItemId(item.getId());
    lot.setLotNumber(lotNumber);
    lot.setExpirationDate(resolveExpirationDate(item, formExpirationDate));
    itemLotMapper.insertLot(lot);
    return itemLotMapper.findById(lot.getId()).orElse(lot);
  }

  private LocalDate resolveExpirationDate(ItemDTO item, LocalDate formExpirationDate) {
    if (item.getExpirationPeriodDays() != null) {
      return LocalDate.now(clock).plusDays(item.getExpirationPeriodDays());
    }
    return formExpirationDate;
  }

  private List<String> parseManualSerials(String serialNumbersText) {
    if (serialNumbersText == null || serialNumbersText.isBlank()) {
      throw new IllegalArgumentException(getMsg("error.serial.required"));
    }
    return serialNumbersText.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
  }

  private void validateInboundCount(int count) {
    if (count < 1 || count > MAX_INBOUND_COUNT) {
      throw new IllegalArgumentException(getMsg("error.stock.countRange", MAX_INBOUND_COUNT));
    }
  }

  private void rejectDuplicateSerials(List<String> serialNumbers) {
    Set<String> seen = new HashSet<>();
    for (String serialNumber : serialNumbers) {
      if (!seen.add(serialNumber)) {
        throw new IllegalArgumentException(getMsg("error.serial.duplicate", serialNumber));
      }
    }
  }

  private void rejectExistingSerials(Long itemId, List<String> serialNumbers) {
    List<String> nonBlankSerials =
        serialNumbers.stream()
            .filter(Objects::nonNull)
            .filter(serial -> !serial.isBlank())
            .toList();
    if (nonBlankSerials.isEmpty()) {
      return;
    }
    List<String> existing = stockMapper.findExistingSerialNumbers(itemId, nonBlankSerials);
    if (!existing.isEmpty()) {
      throw new IllegalArgumentException(getMsg("error.serial.exists", existing.get(0)));
    }
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
