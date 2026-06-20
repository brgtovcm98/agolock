package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.ItemTransactionHistoryDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.enumeration.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockTransactionMapper {
  void insertTransaction(StockTransactionDTO transaction);

  void insertTransactions(List<StockTransactionDTO> transactions);

  Optional<StockTransactionDTO> findById(Long id);

  List<StockTransactionDTO> findByStockId(Long stockId);

  List<String> findFrequentMemosByUserIdAndType(
      @Param("userId") Long userId,
      @Param("transactionType") TransactionType transactionType,
      @Param("limit") int limit);

  List<ItemTransactionHistoryDTO> findHistoryByItemExternalId(
      @Param("itemExternalId") UUID itemExternalId, @Param("userId") Long userId);

  List<ItemTransactionHistoryDTO> findHistoryByStockExternalId(
      @Param("stockExternalId") UUID stockExternalId, @Param("userId") Long userId);
}
