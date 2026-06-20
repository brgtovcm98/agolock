package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.SpaceSummaryDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpaceMapper {
  void insertSpace(SpaceDTO space);

  Optional<SpaceDTO> findById(Long id);

  Optional<SpaceDTO> findByExternalId(UUID externalId);

  List<SpaceDTO> findByUserId(Long userId);

  List<SpaceDTO> findByUserIdWithOptions(
      @Param("userId") Long userId,
      @Param("keyword") String keyword,
      @Param("sortBy") String sortBy,
      @Param("limit") int limit,
      @Param("offset") int offset);

  int countByUserIdWithOptions(@Param("userId") Long userId, @Param("keyword") String keyword);

  void updateSpace(SpaceDTO space);

  void deleteById(Long id);

  List<SpaceSummaryDTO> findSummariesBySpaceIds(
      @Param("spaceIds") List<Long> spaceIds,
      @Param("today") LocalDate today,
      @Param("soonCutoff") LocalDate soonCutoff);
}
