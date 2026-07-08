package com.seu.seustock.model.enumeration;

public enum StockStatus {
  IN_STOCK("재고"),
  DISPATCHED("사용"),
  LOST("분실"),
  DAMAGED("파손"),
  DISPOSED("폐기");

  private final String label;

  StockStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
