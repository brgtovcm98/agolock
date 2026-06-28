package com.seu.seustock.service;

import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.SpaceSummaryDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.SpaceForm;
import com.seu.seustock.model.pagination.PageRequest;
import com.seu.seustock.model.pagination.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceService {

  private final SpaceMapper spaceMapper;
  private final UserMapper userMapper;
  private final StockMapper stockMapper;
  private final MessageSource messageSource;

  @Value("${seustock.space.expiring-soon-days:7}")
  private int expiringSoonDays;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /**
   * Aggregate per-space summary metrics for the space-list strip, keyed by space external id.
   * Returns an empty map for an empty input (avoids an empty SQL IN clause). The expiry window
   * upper bound is {@code today + expiringSoonDays}.
   */
  public Map<UUID, SpaceSummaryDTO> findSummariesByExternalId(List<SpaceDTO> spaces) {
    if (spaces.isEmpty()) {
      return Map.of();
    }
    List<Long> spaceIds = spaces.stream().map(SpaceDTO::getId).toList();
    LocalDate today = LocalDate.now();
    return spaceMapper
        .findSummariesBySpaceIds(spaceIds, today, today.plusDays(expiringSoonDays))
        .stream()
        .collect(Collectors.toMap(SpaceSummaryDTO::getSpaceExternalId, Function.identity()));
  }

  public List<SpaceDTO> findAllByUsername(String username) {
    UserDTO user = getUser(username);
    return spaceMapper.findByUserId(user.getId());
  }

  public List<SpaceDTO> findAllByUsername(String username, String keyword, String sortBy) {
    return findPageByUsername(username, keyword, sortBy, 1).content();
  }

  public PageResult<SpaceDTO> findPageByUsername(
      String username, String keyword, String sortBy, Integer page) {
    UserDTO user = getUser(username);
    String effectiveKeyword = normalizeKeyword(keyword);
    int totalCount = spaceMapper.countByUserIdWithOptions(user.getId(), effectiveKeyword);
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<SpaceDTO> spaces =
        spaceMapper.findByUserIdWithOptions(
            user.getId(),
            effectiveKeyword,
            normalizeSort(sortBy),
            pageRequest.size(),
            pageRequest.offset());
    return new PageResult<>(spaces, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public SpaceDTO findByExternalId(UUID externalId, String username) {
    SpaceDTO space = getSpace(externalId);
    verifyOwner(space, username);
    return space;
  }

  public SpaceDTO findById(Long id) {
    return spaceMapper
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.space.notFound")));
  }

  public Long getUserIdByUsername(String username) {
    return getUser(username).getId();
  }

  public SpaceDTO create(String username, SpaceForm form) {
    UserDTO user = getUser(username);
    SpaceDTO space = new SpaceDTO();
    space.setUserId(user.getId());
    space.setName(form.getName());
    spaceMapper.insertSpace(space);
    log.info("space created userId={} spaceId={}", user.getId(), space.getId());
    if (space.getId() == null) {
      return space;
    }
    return spaceMapper.findById(space.getId()).orElse(space);
  }

  public SpaceDTO update(UUID externalId, SpaceForm form, String username) {
    SpaceDTO space = getSpace(externalId);
    verifyOwner(space, username);
    space.setName(form.getName());
    spaceMapper.updateSpace(space);
    log.info("space updated userId={} spaceId={}", getUser(username).getId(), space.getId());
    return spaceMapper.findByExternalId(externalId).orElseThrow();
  }

  public void delete(UUID externalId, String username) {
    SpaceDTO space = getSpace(externalId);
    verifyOwner(space, username);
    if (!stockMapper.findBySpaceId(space.getId()).isEmpty()) {
      log.warn(
          "space delete rejected userId={} spaceId={} reason=has_stock",
          getUser(username).getId(),
          space.getId());
      throw new IllegalStateException(getMsg("error.space.hasStock"));
    }
    spaceMapper.deleteById(space.getId());
    log.info("space deleted userId={} spaceId={}", getUser(username).getId(), space.getId());
  }

  private UserDTO getUser(String username) {
    return userMapper
        .findByEmail(username)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  private String normalizeSort(String sortBy) {
    return sortBy == null || sortBy.isBlank() ? "most_stock" : sortBy;
  }

  private SpaceDTO getSpace(UUID externalId) {
    return spaceMapper
        .findByExternalId(externalId)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.space.notFound")));
  }

  private void verifyOwner(SpaceDTO space, String username) {
    UserDTO user = getUser(username);
    if (!space.getUserId().equals(user.getId())) {
      log.warn("access denied userId={} resource=space resourceId={}", user.getId(), space.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
  }
}
