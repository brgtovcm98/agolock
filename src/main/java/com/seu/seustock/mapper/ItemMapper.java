package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.ItemDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper {
  void insertItem(ItemDTO item);

  Optional<ItemDTO> findById(Long id);

  Optional<ItemDTO> findByExternalId(UUID externalId);

  List<ItemDTO> findByUserId(Long userId);

  List<ItemDTO> findByUserIdWithOptions(
      @Param("userId") Long userId,
      @Param("keyword") String keyword,
      @Param("searchType") String searchType,
      @Param("sortBy") String sortBy,
      @Param("limit") int limit,
      @Param("offset") int offset);

  int countByUserIdWithOptions(
      @Param("userId") Long userId,
      @Param("keyword") String keyword,
      @Param("searchType") String searchType);

  void updateItem(ItemDTO item);

  void updateSerialNextSequence(
      @Param("id") Long id, @Param("serialNextSequence") long serialNextSequence);

  void updateLotSequence(
      @Param("id") Long id,
      @Param("lotSequenceKey") String lotSequenceKey,
      @Param("lotNextSequence") int lotNextSequence);

  void deactivateById(Long id);

  void deleteById(Long id);
}
