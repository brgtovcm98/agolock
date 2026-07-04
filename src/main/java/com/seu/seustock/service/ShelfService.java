package com.seu.seustock.service;

import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.IdCountDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.ShelfForm;
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
public class ShelfService extends BaseService {

  private final ShelfMapper shelfMapper;
  private final SpaceMapper spaceMapper;
  private final StockMapper stockMapper;

  public ShelfService(
      ShelfMapper shelfMapper,
      SpaceMapper spaceMapper,
      StockMapper stockMapper,
      UserMapper userMapper,
      MessageSource messageSource) {
    super(userMapper, messageSource);
    this.shelfMapper = shelfMapper;
    this.spaceMapper = spaceMapper;
    this.stockMapper = stockMapper;
  }

  public List<ShelfDTO> findAllBySpaceId(UUID spaceExternalId, String username) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, username);
    List<ShelfDTO> shelves = shelfMapper.findBySpaceId(space.getId());
    if (shelves.isEmpty()) {
      return shelves;
    }
    List<Long> shelfIds = shelves.stream().map(ShelfDTO::getId).toList();
    Map<Long, Integer> counts =
        stockMapper.countInStockByShelfIds(shelfIds).stream()
            .collect(Collectors.toMap(IdCountDTO::getId, IdCountDTO::getCount));
    for (ShelfDTO shelf : shelves) {
      shelf.setStockCount(counts.getOrDefault(shelf.getId(), 0));
    }
    return shelves;
  }

  public ShelfDTO findByExternalId(UUID spaceExternalId, UUID shelfExternalId, String username) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, username);
    ShelfDTO shelf = getShelf(shelfExternalId);
    verifyShelfOwnership(shelf, space);
    return shelf;
  }

  public ShelfDTO findById(Long id) {
    return shelfMapper
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
  }

  public ShelfDTO findByExternalIdOnly(UUID externalId) {
    return getShelf(externalId);
  }

  @Transactional
  public ShelfDTO create(UUID spaceExternalId, ShelfForm form, String username) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, username);
    ShelfDTO shelf = new ShelfDTO();
    shelf.setSpaceId(space.getId());
    shelf.setName(form.getName());
    shelfMapper.insertShelf(shelf);
    log.info(
        "shelf created userId={} spaceId={} shelfId={}",
        getUser(username).getId(),
        space.getId(),
        shelf.getId());
    return shelfMapper
        .findById(shelf.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
  }

  @Transactional
  public ShelfDTO rename(
      UUID spaceExternalId, UUID shelfExternalId, ShelfForm form, String username) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, username);
    ShelfDTO shelf = getShelf(shelfExternalId);
    verifyShelfOwnership(shelf, space);
    shelf.setName(form.getName());
    shelfMapper.updateShelf(shelf);
    log.info(
        "shelf renamed userId={} spaceId={} shelfId={}",
        getUser(username).getId(),
        space.getId(),
        shelf.getId());
    return shelfMapper
        .findById(shelf.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
  }

  @Transactional
  public void delete(UUID spaceExternalId, UUID shelfExternalId, String username) {
    SpaceDTO space = getVerifiedSpace(spaceExternalId, username);
    ShelfDTO shelf = getShelf(shelfExternalId);
    verifyShelfOwnership(shelf, space);
    shelfMapper.deleteById(shelf.getId());
    log.info(
        "shelf deleted userId={} spaceId={} shelfId={}",
        getUser(username).getId(),
        space.getId(),
        shelf.getId());
  }

  SpaceDTO getVerifiedSpace(UUID spaceExternalId, String username) {
    SpaceDTO space =
        spaceMapper
            .findByExternalId(spaceExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.space.notFound")));
    UserDTO user =
        userMapper
            .findByEmail(username)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
    if (!space.getUserId().equals(user.getId())) {
      log.warn("access denied userId={} resource=space resourceId={}", user.getId(), space.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
    return space;
  }

  private ShelfDTO getShelf(UUID shelfExternalId) {
    return shelfMapper
        .findByExternalId(shelfExternalId)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
  }

  private void verifyShelfOwnership(ShelfDTO shelf, SpaceDTO space) {
    if (!shelf.getSpaceId().equals(space.getId())) {
      log.warn(
          "access denied resource=shelf resourceId={} spaceId={}", shelf.getId(), space.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
  }
}
