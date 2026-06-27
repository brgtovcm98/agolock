package com.seu.seustock.service;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockLocationVerifier {

  private final SpaceMapper spaceMapper;
  private final ShelfMapper shelfMapper;
  private final BoxMapper boxMapper;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  VerifiedStockLocation resolve(
      UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId, UserDTO user) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, user);
    ShelfDTO shelf = null;
    BoxDTO box = null;

    if (boxExternalId != null && shelfExternalId == null) {
      log.warn(
          "location rejected userId={} reason=box_requires_shelf boxExternalId={}",
          user.getId(),
          boxExternalId);
      throw new IllegalArgumentException(getMsg("error.box.requiresShelf"));
    }

    if (shelfExternalId != null) {
      shelf =
          shelfMapper
              .findByExternalId(shelfExternalId)
              .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
      if (!shelf.getSpaceId().equals(space.getId())) {
        log.warn(
            "access denied userId={} resource=shelf resourceId={} spaceId={}",
            user.getId(),
            shelf.getId(),
            space.getId());
        throw new SecurityException(getMsg("error.403.title"));
      }
    }

    if (boxExternalId != null) {
      box =
          boxMapper
              .findByExternalId(boxExternalId)
              .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
      if (!box.getShelfId().equals(shelf.getId())) {
        log.warn(
            "access denied userId={} resource=box resourceId={} shelfId={}",
            user.getId(),
            box.getId(),
            shelf.getId());
        throw new SecurityException(getMsg("error.403.title"));
      }
    }

    return new VerifiedStockLocation(space, shelf, box);
  }

  SpaceDTO getVerifiedSpace(UUID spaceExternalId, UserDTO user) {
    SpaceDTO space =
        spaceMapper
            .findByExternalId(spaceExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.space.notFound")));
    if (!space.getUserId().equals(user.getId())) {
      log.warn("access denied userId={} resource=space resourceId={}", user.getId(), space.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
    return space;
  }

  boolean isSameLocation(VerifiedStockLocation source, VerifiedStockLocation target) {
    return Objects.equals(source.space().getId(), target.space().getId())
        && Objects.equals(source.shelfId(), target.shelfId())
        && Objects.equals(source.boxId(), target.boxId());
  }
}
