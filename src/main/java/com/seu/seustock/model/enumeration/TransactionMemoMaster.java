package com.seu.seustock.model.enumeration;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum TransactionMemoMaster {
  PURCHASE_IN(TransactionType.IN, "enum.TransactionMemoMaster.PURCHASE_IN"),
  RETURN_IN(TransactionType.IN, "enum.TransactionMemoMaster.RETURN_IN"),
  FOUND_IN(TransactionType.IN, "enum.TransactionMemoMaster.FOUND_IN"),
  ADJUSTMENT_IN(TransactionType.IN, "enum.TransactionMemoMaster.ADJUSTMENT_IN"),

  USE_OUT(TransactionType.OUT, "enum.TransactionMemoMaster.USE_OUT"),
  SALES_OUT(TransactionType.OUT, "enum.TransactionMemoMaster.SALES_OUT"),
  DISPOSAL_OUT(TransactionType.OUT, "enum.TransactionMemoMaster.DISPOSAL_OUT"),
  LOST_OUT(TransactionType.OUT, "enum.TransactionMemoMaster.LOST_OUT");

  private final TransactionType transactionType;
  private final String messageKey;

  TransactionMemoMaster(TransactionType transactionType, String messageKey) {
    this.transactionType = transactionType;
    this.messageKey = messageKey;
  }

  public TransactionType getTransactionType() {
    return transactionType;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public static Optional<String> messageKeyForStoredValue(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String trimmed = value.strip();
    return Arrays.stream(values())
        .filter(master -> master.name().equals(trimmed) || master.messageKey.equals(trimmed))
        .map(TransactionMemoMaster::getMessageKey)
        .findFirst();
  }

  public static List<String> messageKeysFor(TransactionType transactionType) {
    return Arrays.stream(values())
        .filter(master -> master.transactionType == transactionType)
        .map(TransactionMemoMaster::getMessageKey)
        .toList();
  }
}
