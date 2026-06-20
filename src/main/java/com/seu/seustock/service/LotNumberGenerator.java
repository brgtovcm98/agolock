package com.seu.seustock.service;

import com.seu.seustock.model.dto.ItemDTO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class LotNumberGenerator {

  public Result generate(ItemDTO item, LocalDate date) {
    String pattern =
        item.getLotDateFormat() == null || item.getLotDateFormat().isBlank()
            ? "yyyyMMdd"
            : item.getLotDateFormat();
    String key = date.format(DateTimeFormatter.ofPattern(pattern));
    int sequence = key.equals(item.getLotSequenceKey()) ? item.getLotNextSequence() + 1 : 1;
    String prefix = item.getLotVendorCode() == null ? "" : item.getLotVendorCode().trim();
    String lotNumber = prefix + key;
    if (item.isLotIncludeSequence()) {
      lotNumber += "-" + "%03d".formatted(sequence);
    }
    return new Result(lotNumber, key, sequence);
  }

  public record Result(String lotNumber, String sequenceKey, int nextSequence) {}
}
