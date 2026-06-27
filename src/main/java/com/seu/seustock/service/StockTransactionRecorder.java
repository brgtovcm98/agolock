package com.seu.seustock.service;

import com.seu.seustock.mapper.StockTransactionMapper;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.enumeration.TransactionType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockTransactionRecorder {

  private final StockTransactionMapper transactionMapper;

  void recordInbound(List<StockDTO> units, String memo) {
    List<StockTransactionDTO> txs = new ArrayList<>(units.size());
    for (StockDTO unit : units) {
      StockTransactionDTO tx = new StockTransactionDTO();
      tx.setStockId(unit.getId());
      tx.setTransactionType(TransactionType.IN);
      tx.setMemo(memo);
      txs.add(tx);
    }
    transactionMapper.insertTransactions(txs);
  }

  void recordOutbound(StockDTO unit, String memo) {
    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(unit.getId());
    tx.setTransactionType(TransactionType.OUT);
    tx.setMemo(memo);
    transactionMapper.insertTransaction(tx);
  }

  void recordAdjustment(Long stockId, String memo) {
    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(stockId);
    tx.setTransactionType(TransactionType.ADJUST);
    tx.setMemo(memo);
    transactionMapper.insertTransaction(tx);
  }

  void recordStatusChange(Long stockId, TransactionType transactionType, String memo) {
    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(stockId);
    tx.setTransactionType(transactionType);
    tx.setMemo(memo);
    transactionMapper.insertTransaction(tx);
  }

  void recordMove(
      StockDTO unit, VerifiedStockLocation source, VerifiedStockLocation target, String memo) {
    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(unit.getId());
    tx.setTransactionType(TransactionType.MOVE);
    tx.setFromSpaceId(source.space().getId());
    tx.setFromShelfId(source.shelfId());
    tx.setFromBoxId(source.boxId());
    tx.setToSpaceId(target.space().getId());
    tx.setToShelfId(target.shelfId());
    tx.setToBoxId(target.boxId());
    tx.setMemo(memo);
    transactionMapper.insertTransaction(tx);
  }
}
