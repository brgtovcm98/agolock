package com.seu.seustock.service;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.BoxForm;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoxService {

  private final BoxMapper boxMapper;
  private final ShelfMapper shelfMapper;
  private final SpaceMapper spaceMapper;
  private final UserMapper userMapper;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  public BoxDTO findByExternalId(
      UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId, String username) {
    ShelfDTO shelf = getVerifiedShelf(spaceExternalId, shelfExternalId, username);
    BoxDTO box =
        boxMapper
            .findByExternalId(boxExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
    if (!box.getShelfId().equals(shelf.getId())) {
      log.warn("access denied resource=box resourceId={} shelfId={}", box.getId(), shelf.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
    return box;
  }

  public BoxDTO findById(Long id) {
    return boxMapper
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
  }

  public BoxDTO findByExternalIdOnly(UUID externalId) {
    return boxMapper
        .findByExternalId(externalId)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
  }

  public List<BoxDTO> findAllByShelfId(
      UUID spaceExternalId, UUID shelfExternalId, String username) {
    ShelfDTO shelf = getVerifiedShelf(spaceExternalId, shelfExternalId, username);
    return boxMapper.findByShelfId(shelf.getId());
  }

  public BoxDTO create(UUID spaceExternalId, UUID shelfExternalId, BoxForm form, String username) {
    ShelfDTO shelf = getVerifiedShelf(spaceExternalId, shelfExternalId, username);
    BoxDTO box = new BoxDTO();
    box.setShelfId(shelf.getId());
    box.setName(form.getName());
    boxMapper.insertBox(box);
    log.info(
        "box created userId={} shelfId={} boxId={}",
        getUser(username).getId(),
        shelf.getId(),
        box.getId());
    return boxMapper.findById(box.getId()).orElseThrow();
  }

  public void rename(
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      BoxForm form,
      String username) {
    ShelfDTO shelf = getVerifiedShelf(spaceExternalId, shelfExternalId, username);
    BoxDTO box =
        boxMapper
            .findByExternalId(boxExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
    if (!box.getShelfId().equals(shelf.getId())) {
      log.warn("access denied resource=box resourceId={} shelfId={}", box.getId(), shelf.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
    box.setName(form.getName());
    boxMapper.updateBox(box);
    log.info(
        "box renamed userId={} shelfId={} boxId={}",
        getUser(username).getId(),
        shelf.getId(),
        box.getId());
  }

  public void delete(
      UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId, String username) {
    ShelfDTO shelf = getVerifiedShelf(spaceExternalId, shelfExternalId, username);
    BoxDTO box =
        boxMapper
            .findByExternalId(boxExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
    if (!box.getShelfId().equals(shelf.getId())) {
      log.warn("access denied resource=box resourceId={} shelfId={}", box.getId(), shelf.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
    boxMapper.deleteById(box.getId());
    log.info(
        "box deleted userId={} shelfId={} boxId={}",
        getUser(username).getId(),
        shelf.getId(),
        box.getId());
  }

  private ShelfDTO getVerifiedShelf(UUID spaceExternalId, UUID shelfExternalId, String username) {
    SpaceDTO space =
        spaceMapper
            .findByExternalId(spaceExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.space.notFound")));
    UserDTO user =
        userMapper
            .findByEmail(username)
            .filter(u -> u.getId().equals(space.getUserId()))
            .orElseThrow(() -> new SecurityException(getMsg("error.403.title")));
    ShelfDTO shelf =
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
    return shelf;
  }

  private UserDTO getUser(String username) {
    return userMapper
        .findByEmail(username)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
  }
}
