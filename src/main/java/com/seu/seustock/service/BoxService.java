package com.seu.seustock.service;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.IdCountDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.BoxForm;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BoxService extends BaseService {

  private final BoxMapper boxMapper;
  private final ShelfMapper shelfMapper;
  private final SpaceMapper spaceMapper;
  private final StockMapper stockMapper;

  public BoxService(
      BoxMapper boxMapper,
      ShelfMapper shelfMapper,
      SpaceMapper spaceMapper,
      StockMapper stockMapper,
      UserMapper userMapper,
      MessageSource messageSource) {
    super(userMapper, messageSource);
    this.boxMapper = boxMapper;
    this.shelfMapper = shelfMapper;
    this.spaceMapper = spaceMapper;
    this.stockMapper = stockMapper;
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
    List<BoxDTO> boxes = boxMapper.findByShelfId(shelf.getId());
    if (boxes.isEmpty()) {
      return boxes;
    }
    List<Long> boxIds = boxes.stream().map(BoxDTO::getId).toList();
    Map<Long, Integer> counts =
        stockMapper.countPanelByBoxIds(boxIds).stream()
            .collect(Collectors.toMap(IdCountDTO::getId, IdCountDTO::getCount));
    for (BoxDTO box : boxes) {
      box.setStockCount(counts.getOrDefault(box.getId(), 0));
    }
    return boxes;
  }

  @Transactional
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
    return boxMapper
        .findById(box.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
  }

  @Transactional
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

  @Transactional
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
}
