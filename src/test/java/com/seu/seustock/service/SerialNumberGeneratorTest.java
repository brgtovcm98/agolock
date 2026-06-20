package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SerialNumberGeneratorTest {

  private final SerialNumberGenerator generator = new SerialNumberGenerator();

  @Test
  void generate_appliesPrefixPaddingIncrementAndNextSequence() {
    SerialNumberGenerator.Result result = generator.generate("SEU-", 4, 2, 0, 3);

    assertThat(result.serialNumbers()).containsExactly("SEU-0002", "SEU-0004", "SEU-0006");
    assertThat(result.nextSequence()).isEqualTo(6);
  }

  @Test
  void generate_omitsPaddingWhenLengthIsZero() {
    SerialNumberGenerator.Result result = generator.generate(null, 0, 1, 9, 2);

    assertThat(result.serialNumbers()).containsExactly("10", "11");
    assertThat(result.nextSequence()).isEqualTo(11);
  }
}
