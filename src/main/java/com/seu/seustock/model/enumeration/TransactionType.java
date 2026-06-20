package com.seu.seustock.model.enumeration;

public enum TransactionType {
  IN("입고"),
  OUT("출고"),
  MOVE("이동"),
  ADJUST("조정");

  private final String label;

  TransactionType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
