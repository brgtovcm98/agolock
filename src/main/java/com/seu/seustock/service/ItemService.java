package com.seu.seustock.service;

import com.seu.seustock.mapper.ItemImageMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.StockTransactionMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ItemSpaceStockDTO;
import com.seu.seustock.model.dto.ItemTransactionHistoryDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.TrackingMode;
import com.seu.seustock.model.form.ItemForm;
import com.seu.seustock.model.pagination.PageRequest;
import com.seu.seustock.model.pagination.PageResult;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ItemService extends BaseService {

  private final ItemMapper itemMapper;
  private final StockMapper stockMapper;
  private final ItemImageMapper itemImageMapper;
  private final ImageStorageService imageStorageService;
  private final StockTransactionMapper transactionMapper;

  public ItemService(
      ItemMapper itemMapper,
      UserMapper userMapper,
      StockMapper stockMapper,
      ItemImageMapper itemImageMapper,
      ImageStorageService imageStorageService,
      StockTransactionMapper transactionMapper,
      MessageSource messageSource) {
    super(userMapper, messageSource);
    this.itemMapper = itemMapper;
    this.stockMapper = stockMapper;
    this.itemImageMapper = itemImageMapper;
    this.imageStorageService = imageStorageService;
    this.transactionMapper = transactionMapper;
  }

  public List<ItemDTO> findAllByUsername(String username) {
    UserDTO user = getUser(username);
    return itemMapper.findByUserId(user.getId());
  }

  public List<ItemDTO> findAllByUsername(String username, String keyword, String sortBy) {
    return findPageByUsername(username, keyword, "name", sortBy, 1).content();
  }

  public PageResult<ItemDTO> findPageByUsername(
      String username, String keyword, String sortBy, Integer page) {
    return findPageByUsername(username, keyword, "name", sortBy, page);
  }

  public PageResult<ItemDTO> findPageByUsername(
      String username, String keyword, String searchType, String sortBy, Integer page) {
    UserDTO user = getUser(username);
    String effectiveKeyword = normalizeKeyword(keyword);
    String effectiveSearchType = normalizeSearchType(searchType);
    int totalCount =
        itemMapper.countByUserIdWithOptions(user.getId(), effectiveKeyword, effectiveSearchType);
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<ItemDTO> items =
        itemMapper.findByUserIdWithOptions(
            user.getId(),
            effectiveKeyword,
            effectiveSearchType,
            normalizeSort(sortBy),
            pageRequest.size(),
            pageRequest.offset());
    return new PageResult<>(items, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public ItemDTO findByExternalId(UUID externalId, String username) {
    ItemDTO item = getItem(externalId);
    verifyOwner(item, username);
    return item;
  }

  @Transactional
  public ItemDTO create(String username, ItemForm form) {
    UserDTO user = getUser(username);
    ItemDTO item = new ItemDTO();
    item.setUserId(user.getId());
    item.setName(form.getName());
    item.setDescription(form.getDescription());
    item.setPrice(form.getPrice());
    applyPolicy(item, form);
    itemMapper.insertItem(item);
    attachPrimaryImageIfPresent(item.getId(), user, form);
    log.info("item created userId={} itemId={}", user.getId(), item.getId());
    return itemMapper.findById(item.getId()).orElseThrow();
  }

  @Transactional
  public ItemDTO update(UUID externalId, ItemForm form, String username) {
    ItemDTO item = getItem(externalId);
    verifyOwner(item, username);
    item.setName(form.getName());
    item.setDescription(form.getDescription());
    item.setPrice(form.getPrice());
    applyPolicy(item, form);
    itemMapper.updateItem(item);
    attachPrimaryImageIfPresent(item.getId(), getUser(username), form);
    log.info("item updated userId={} itemId={}", getUser(username).getId(), item.getId());
    return itemMapper.findByExternalId(externalId).orElseThrow();
  }

  public List<ItemSpaceStockDTO> findSpaceStock(UUID itemExternalId, String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getItem(itemExternalId);
    verifyOwner(item, username);
    return stockMapper.findSpaceStockByItem(itemExternalId, user.getId());
  }

  public List<ItemTransactionHistoryDTO> findTransactionHistory(
      UUID itemExternalId, String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getItem(itemExternalId);
    verifyOwner(item, username);
    return transactionMapper.findHistoryByItemExternalId(itemExternalId, user.getId());
  }

  @Transactional
  public void delete(UUID externalId, String username) {
    ItemDTO item = getItem(externalId);
    verifyOwner(item, username);
    if (stockMapper.countInStockByItemId(item.getId()) > 0) {
      log.warn(
          "item delete rejected userId={} itemId={} reason=has_in_stock",
          getUser(username).getId(),
          item.getId());
      throw new IllegalStateException(getMsg("error.item.hasStock"));
    }
    if (stockMapper.countByItemId(item.getId()) > 0) {
      itemMapper.deactivateById(item.getId());
      log.info("item deactivated userId={} itemId={}", getUser(username).getId(), item.getId());
      return;
    }
    itemMapper.deleteById(item.getId());
    log.info("item deleted userId={} itemId={}", getUser(username).getId(), item.getId());
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  private String normalizeSearchType(String searchType) {
    return "description".equals(searchType) ? "description" : "name";
  }

  private String normalizeSort(String sortBy) {
    return sortBy == null || sortBy.isBlank() ? "newest" : sortBy;
  }

  private ItemDTO getItem(UUID externalId) {
    return itemMapper
        .findByExternalId(externalId)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.item.notFound")));
  }

  private void verifyOwner(ItemDTO item, String username) {
    UserDTO user = getUser(username);
    if (!item.getUserId().equals(user.getId())) {
      log.warn("access denied userId={} resource=item resourceId={}", user.getId(), item.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
  }

  private void attachPrimaryImageIfPresent(Long itemId, UserDTO user, ItemForm form) {
    ImageDTO image = imageStorageService.store(form.getImageFile(), user, form.getImageHash());
    if (image == null) {
      return;
    }
    itemImageMapper.unsetPrimaryByItemId(itemId);
    if (itemImageMapper.countByItemIdAndImageId(itemId, image.getId()) > 0) {
      itemImageMapper.updateItemImage(itemId, image.getId(), 0, true);
      log.info(
          "item primary image updated userId={} itemId={} imageId={}",
          user.getId(),
          itemId,
          image.getId());
      return;
    }
    itemImageMapper.insertItemImage(itemId, image.getId(), 0, true);
    log.info(
        "item primary image attached userId={} itemId={} imageId={}",
        user.getId(),
        itemId,
        image.getId());
  }

  private void applyPolicy(ItemDTO item, ItemForm form) {
    item.setSerialMode(form.getSerialMode() == null ? TrackingMode.NONE : form.getSerialMode());
    item.setSerialPrefix(blankToNull(form.getSerialPrefix()));
    item.setSerialPaddingLength(form.getSerialPaddingLength());
    item.setSerialIncrementUnit(form.getSerialIncrementUnit());
    item.setSerialNextSequence(form.getSerialNextSequence());
    item.setLotMode(form.getLotMode() == null ? TrackingMode.NONE : form.getLotMode());
    item.setLotVendorCode(blankToNull(form.getLotVendorCode()));
    item.setLotDateFormat(blankToDefault(form.getLotDateFormat(), "yyyyMMdd"));
    item.setLotIncludeSequence(form.isLotIncludeSequence());
    item.setLotNextSequence(form.getLotNextSequence());
    item.setExpirationPeriodDays(form.getExpirationPeriodDays());
  }

  private String blankToDefault(String value, String defaultValue) {
    String normalized = blankToNull(value);
    return normalized == null ? defaultValue : normalized;
  }
}
