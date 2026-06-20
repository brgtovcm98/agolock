package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.BoxDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BoxMapper {
  void insertBox(BoxDTO box);

  Optional<BoxDTO> findById(Long id);

  Optional<BoxDTO> findByExternalId(UUID externalId);

  List<BoxDTO> findByShelfId(Long shelfId);

  void updateBox(BoxDTO box);

  void deleteById(Long id);
}
