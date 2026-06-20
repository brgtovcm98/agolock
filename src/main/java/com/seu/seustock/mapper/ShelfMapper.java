package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.ShelfDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShelfMapper {
  void insertShelf(ShelfDTO shelf);

  Optional<ShelfDTO> findById(Long id);

  Optional<ShelfDTO> findByExternalId(UUID externalId);

  List<ShelfDTO> findBySpaceId(Long spaceId);

  void updateShelf(ShelfDTO shelf);

  void deleteById(Long id);
}
