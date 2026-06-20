package com.seu.seustock.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SerialNumberGenerator {

  public Result generate(
      String prefix, int paddingLength, int incrementUnit, long nextSequence, int count) {
    List<String> serialNumbers = new ArrayList<>(count);
    long current = nextSequence;
    String effectivePrefix = prefix == null ? "" : prefix;
    for (int i = 0; i < count; i++) {
      current += incrementUnit;
      serialNumbers.add(effectivePrefix + pad(current, paddingLength));
    }
    return new Result(serialNumbers, current);
  }

  private String pad(long value, int paddingLength) {
    String raw = Long.toString(value);
    if (paddingLength <= raw.length()) {
      return raw;
    }
    return "0".repeat(paddingLength - raw.length()) + raw;
  }

  public record Result(List<String> serialNumbers, long nextSequence) {}
}
