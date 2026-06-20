package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.ItemDTO;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LotNumberGeneratorTest {

  private final LotNumberGenerator generator = new LotNumberGenerator();

  @Test
  void generate_incrementsSequenceForSameDateKey() {
    ItemDTO item = new ItemDTO();
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(true);
    item.setLotSequenceKey("20260531");
    item.setLotNextSequence(3);

    LotNumberGenerator.Result result = generator.generate(item, LocalDate.of(2026, 5, 31));

    assertThat(result.lotNumber()).isEqualTo("20260531-004");
    assertThat(result.sequenceKey()).isEqualTo("20260531");
    assertThat(result.nextSequence()).isEqualTo(4);
  }

  @Test
  void generate_resetsSequenceForNewDateKeyAndAppliesVendorCode() {
    ItemDTO item = new ItemDTO();
    item.setLotVendorCode("VN-");
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(true);
    item.setLotSequenceKey("20260530");
    item.setLotNextSequence(9);

    LotNumberGenerator.Result result = generator.generate(item, LocalDate.of(2026, 5, 31));

    assertThat(result.lotNumber()).isEqualTo("VN-20260531-001");
    assertThat(result.nextSequence()).isEqualTo(1);
  }

  @Test
  void generate_canOmitSequenceSuffix() {
    ItemDTO item = new ItemDTO();
    item.setLotDateFormat("yyyyMMdd");
    item.setLotIncludeSequence(false);

    LotNumberGenerator.Result result = generator.generate(item, LocalDate.of(2026, 5, 31));

    assertThat(result.lotNumber()).isEqualTo("20260531");
    assertThat(result.nextSequence()).isEqualTo(1);
  }
}
