package com.seu.seustock.service;

import java.math.BigDecimal;
import java.time.LocalDate;

record StockInboundSpec(
    int count,
    String serialNumber,
    String serialNumbersText,
    String lotNumber,
    LocalDate expirationDate,
    BigDecimal price,
    String memo) {}
