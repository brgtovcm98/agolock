package com.seu.seustock.service;

import com.seu.seustock.mapper.*;
import com.seu.seustock.model.dto.*;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TransactionMemoMaster;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.model.form.QuickStockForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.model.form.StockInOutForm;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.model.form.StockUpdateForm;
import com.seu.seustock.model.pagination.PageRequest;
import com.seu.seustock.model.pagination.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StockService extends BaseService {

  private final StockMapper stockMapper;
  private final StockTransactionMapper transactionMapper;
  private final ItemMapper itemMapper;
  private final ItemImageMapper itemImageMapper;
  private final ImageStorageService imageStorageService;
  private final StockLocationVerifier locationVerifier;
  private final StockInboundPreparer inboundPreparer;
  private final StockTransactionRecorder transactionRecorder;
  private static final int MEMO_SUGGESTION_LIMIT = 4;

  @Value("${seustock.space.expiring-soon-days:7}")
  private int expiringSoonDays;

  public StockService(
      StockMapper stockMapper,
      StockTransactionMapper transactionMapper,
      ItemMapper itemMapper,
      ItemImageMapper itemImageMapper,
      ImageStorageService imageStorageService,
      StockLocationVerifier locationVerifier,
      StockInboundPreparer inboundPreparer,
      StockTransactionRecorder transactionRecorder,
      UserMapper userMapper,
      MessageSource messageSource) {
    super(userMapper, messageSource);
    this.stockMapper = stockMapper;
    this.transactionMapper = transactionMapper;
    this.itemMapper = itemMapper;
    this.itemImageMapper = itemImageMapper;
    this.imageStorageService = imageStorageService;
    this.locationVerifier = locationVerifier;
    this.inboundPreparer = inboundPreparer;
    this.transactionRecorder = transactionRecorder;
  }

  public List<StockPanelDTO> findPanelBySpace(UUID spaceExternalId, String username) {
    return findPanelPageBySpace(spaceExternalId, username, 1).content();
  }

  public PageResult<StockPanelDTO> findPanelPageBySpace(
      UUID spaceExternalId, String username, Integer page) {
    UserDTO user = getUser(username);
    SpaceDTO space = locationVerifier.getVerifiedSpace(spaceExternalId, user);
    int totalCount = stockMapper.countPanelBySpaceDirectOnly(space.getId());
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<StockPanelDTO> stocks =
        stockMapper.findPanelBySpaceDirectOnlyPaged(
            space.getId(), pageRequest.size(), pageRequest.offset());
    return new PageResult<>(stocks, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public List<StockPanelDTO> findPanelBySpaceAll(
      UUID spaceExternalId, String keyword, String sortBy, String username) {
    return findPanelPageBySpaceAll(spaceExternalId, keyword, sortBy, username, 1).content();
  }

  public PageResult<StockPanelDTO> findPanelPageBySpaceAll(
      UUID spaceExternalId, String keyword, String sortBy, String username, Integer page) {
    UserDTO user = getUser(username);
    SpaceDTO space = locationVerifier.getVerifiedSpace(spaceExternalId, user);
    String effectiveKeyword = normalizeKeyword(keyword);
    int totalCount = stockMapper.countPanelBySpaceAllWithOptions(space.getId(), effectiveKeyword);
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<StockPanelDTO> stocks =
        stockMapper.findPanelBySpaceAllWithOptions(
            space.getId(),
            effectiveKeyword,
            normalizeSort(sortBy),
            pageRequest.size(),
            pageRequest.offset());
    return new PageResult<>(stocks, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public List<StockPanelDTO> findPanelByShelf(
      UUID spaceExternalId, UUID shelfExternalId, String username) {
    return findPanelPageByShelf(spaceExternalId, shelfExternalId, username, 1).content();
  }

  public PageResult<StockPanelDTO> findPanelPageByShelf(
      UUID spaceExternalId, UUID shelfExternalId, String username, Integer page) {
    UserDTO user = getUser(username);
    VerifiedStockLocation location =
        locationVerifier.resolve(spaceExternalId, shelfExternalId, null, user);
    ShelfDTO shelf = location.shelf();
    int totalCount = stockMapper.countPanelByShelfDirectOnly(shelf.getId());
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<StockPanelDTO> stocks =
        stockMapper.findPanelByShelfDirectOnlyPaged(
            shelf.getId(), pageRequest.size(), pageRequest.offset());
    return new PageResult<>(stocks, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public List<StockPanelDTO> findPanelByBox(
      UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId, String username) {
    return findPanelPageByBox(spaceExternalId, shelfExternalId, boxExternalId, username, 1)
        .content();
  }

  public PageResult<StockPanelDTO> findPanelPageByBox(
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      String username,
      Integer page) {
    UserDTO user = getUser(username);
    VerifiedStockLocation location =
        locationVerifier.resolve(spaceExternalId, shelfExternalId, boxExternalId, user);
    BoxDTO box = location.box();
    int totalCount = stockMapper.countPanelByBoxId(box.getId());
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<StockPanelDTO> stocks =
        stockMapper.findPanelByBoxIdPaged(box.getId(), pageRequest.size(), pageRequest.offset());
    return new PageResult<>(stocks, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public List<StockDetailDTO> searchDetails(
      UUID itemExternalId,
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      String filter,
      String keyword,
      String searchType,
      String sortBy,
      String username) {
    return searchDetailsPage(
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
            filter,
            keyword,
            searchType,
            sortBy,
            username,
            1)
        .content();
  }

  public PageResult<StockDetailDTO> searchDetailsPage(
      UUID itemExternalId,
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      String filter,
      String keyword,
      String searchType,
      String sortBy,
      String username,
      Integer page) {
    UserDTO user = getUser(username);
    String effectiveKeyword = normalizeKeyword(keyword);
    String effectiveSearchType = normalizeSearchType(searchType);
    LocalDate today = LocalDate.now();
    LocalDate soonCutoff = today.plusDays(expiringSoonDays);
    int totalCount =
        stockMapper.countSearchDetails(
            user.getId(),
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
            filter,
            today,
            soonCutoff,
            effectiveKeyword,
            effectiveSearchType);
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<StockDetailDTO> stocks =
        stockMapper.searchDetails(
            user.getId(),
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
            filter,
            today,
            soonCutoff,
            effectiveKeyword,
            effectiveSearchType,
            normalizeSort(sortBy),
            pageRequest.size(),
            pageRequest.offset());
    return new PageResult<>(stocks, pageRequest.page(), pageRequest.size(), totalCount);
  }

  public StockDetailDTO findDetailByExternalId(UUID externalId, String username) {
    UserDTO user = getUser(username);
    return stockMapper
        .findDetailByExternalId(externalId, user.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));
  }

  public List<ItemTransactionHistoryDTO> findUnitHistory(UUID stockExternalId, String username) {
    UserDTO user = getUser(username);
    return transactionMapper.findHistoryByStockExternalId(stockExternalId, user.getId());
  }

  public List<String> findMemoSuggestions(TransactionType transactionType, String username) {
    UserDTO user = getUser(username);
    List<String> frequentMemos =
        transactionMapper
            .findFrequentMemosByUserIdAndType(user.getId(), transactionType, MEMO_SUGGESTION_LIMIT)
            .stream()
            .map(this::displayMemo)
            .toList();
    List<String> masterMemos =
        TransactionMemoMaster.messageKeysFor(transactionType).stream().map(this::getMsg).toList();
    return Stream.concat(frequentMemos.stream(), masterMemos.stream())
        .distinct()
        .limit(MEMO_SUGGESTION_LIMIT)
        .toList();
  }

  private String displayMemo(String memo) {
    return TransactionMemoMaster.messageKeyForStoredValue(memo).map(this::getMsg).orElse(memo);
  }

  @Transactional
  public StockDetailDTO updateDetails(UUID externalId, StockUpdateForm form, String username) {
    UserDTO user = getUser(username);
    normalize(form);
    int updated = stockMapper.updateDetails(externalId, user.getId(), form);
    if (updated != 1) {
      log.warn(
          "stock update rejected userId={} stockExternalId={} reason=not_found",
          user.getId(),
          externalId);
      throw new NoSuchElementException(getMsg("error.stock.notFound"));
    }
    log.info("stock details updated userId={} stockExternalId={}", user.getId(), externalId);
    return stockMapper
        .findDetailByExternalId(externalId, user.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));
  }

  @Transactional
  public void create(StockForm form, String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getVerifiedItem(form.getItemExternalId(), user);
    VerifiedStockLocation location =
        locationVerifier.resolve(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        inboundPreparer.prepareInboundUnits(
            item,
            location,
            new StockInboundSpec(
                form.getCount(),
                form.getSerialNumber(),
                form.getSerialNumbersText(),
                form.getLotNumber(),
                form.getExpirationDate(),
                form.getPrice(),
                form.getMemo()));

    String memo = form.getMemo() != null ? form.getMemo() : getMsg("stock.memo.initial");
    transactionRecorder.recordInbound(units, memo);
    log.info(
        "stock units created userId={} itemId={} spaceId={} shelfId={} boxId={} count={}",
        user.getId(),
        item.getId(),
        location.space().getId(),
        location.shelfId(),
        location.boxId(),
        units.size());
  }

  @Transactional
  public void createWithNewItem(QuickStockForm form, String username) {
    UserDTO user = getUser(username);

    ItemDTO item = new ItemDTO();
    item.setUserId(user.getId());
    item.setName(form.getName());
    item.setDescription(form.getDescription());
    item.setPrice(form.getPrice());
    itemMapper.insertItem(item);
    attachPrimaryImageIfPresent(item.getId(), user, form);

    VerifiedStockLocation location =
        locationVerifier.resolve(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        inboundPreparer.prepareInboundUnits(
            item,
            location,
            new StockInboundSpec(form.getCount(), null, null, null, null, null, form.getMemo()));

    String memo = form.getMemo() != null ? form.getMemo() : getMsg("stock.memo.quick");
    transactionRecorder.recordInbound(units, memo);
    log.info(
        "quick stock created userId={} itemId={} spaceId={} shelfId={} boxId={} count={}",
        user.getId(),
        item.getId(),
        location.space().getId(),
        location.shelfId(),
        location.boxId(),
        units.size());
  }

  @Transactional
  public void addUnits(StockInOutForm form, String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getVerifiedItem(form.getItemExternalId(), user);
    VerifiedStockLocation location =
        locationVerifier.resolve(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        inboundPreparer.prepareInboundUnits(
            item,
            location,
            new StockInboundSpec(
                form.getCount(),
                null,
                form.getSerialNumbersText(),
                form.getLotNumber(),
                form.getExpirationDate(),
                form.getPrice(),
                form.getMemo()));

    transactionRecorder.recordInbound(units, form.getMemo());
    log.info(
        "stock units added userId={} itemId={} spaceId={} shelfId={} boxId={} count={}",
        user.getId(),
        item.getId(),
        location.space().getId(),
        location.shelfId(),
        location.boxId(),
        units.size());
  }

  @Transactional
  public void dispatchUnits(StockInOutForm form, String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getVerifiedItem(form.getItemExternalId(), user);
    VerifiedStockLocation location =
        locationVerifier.resolve(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units;
    if (location.box() != null) {
      units =
          stockMapper.findDispatchableByItemAndBox(
              item.getId(), location.box().getId(), form.isIncludeKept());
    } else if (location.shelf() != null) {
      units =
          stockMapper.findDispatchableByItemAndShelf(
              item.getId(), location.shelf().getId(), form.isIncludeKept());
    } else {
      units =
          stockMapper.findDispatchableByItemAndSpace(
              item.getId(), location.space().getId(), form.isIncludeKept());
    }

    if (units.size() < form.getCount()) {
      log.warn(
          "stock dispatch rejected userId={} itemId={} requested={} available={} includeKept={}",
          user.getId(),
          item.getId(),
          form.getCount(),
          units.size(),
          form.isIncludeKept());
      throw new IllegalArgumentException(getMsg("error.stock.insufficient", units.size()));
    }

    for (StockDTO unit : units.subList(0, form.getCount())) {
      int updated = stockMapper.updateStatusIfInStock(unit.getId(), StockStatus.DISPATCHED);
      if (updated != 1) {
        log.warn(
            "stock dispatch rejected userId={} stockId={} reason=status_changed",
            user.getId(),
            unit.getId());
        throw new IllegalStateException(getMsg("error.stock.statusChanged"));
      }

      transactionRecorder.recordOutbound(unit, form.getMemo());
    }
    log.info(
        "stock units dispatched userId={} itemId={} spaceId={} shelfId={} boxId={} count={} includeKept={}",
        user.getId(),
        item.getId(),
        location.space().getId(),
        location.shelfId(),
        location.boxId(),
        form.getCount(),
        form.isIncludeKept());
  }

  @Transactional
  public StockDetailDTO setKeepStatus(UUID stockExternalId, boolean kept, String username) {
    UserDTO user = getUser(username);
    StockDTO stock =
        stockMapper
            .findByExternalId(stockExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));

    ItemDTO item =
        itemMapper
            .findById(stock.getItemId())
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.item.notFound")));
    verifyItemOwner(item, user);

    if (stock.isKept() == kept) {
      return stockMapper
          .findDetailByExternalId(stockExternalId, user.getId())
          .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));
    }

    int updated = stockMapper.updateIsKept(stockExternalId, user.getId(), kept);
    if (updated != 1) {
      log.warn(
          "stock keep status rejected userId={} stockExternalId={} reason=not_found",
          user.getId(),
          stockExternalId);
      throw new NoSuchElementException(getMsg("error.stock.notFound"));
    }

    transactionRecorder.recordAdjustment(
        stock.getId(), kept ? getMsg("stock.memo.keepSet") : getMsg("stock.memo.keepCleared"));

    log.info(
        "stock keep status updated userId={} stockExternalId={} kept={}",
        user.getId(),
        stockExternalId,
        kept);
    return stockMapper
        .findDetailByExternalId(stockExternalId, user.getId())
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));
  }

  @Transactional
  public void changeStatus(UUID stockExternalId, StockStatus status, String memo, String username) {
    UserDTO user = getUser(username);
    if (status == null || status == StockStatus.IN_STOCK) {
      throw new IllegalArgumentException(getMsg("error.stock.invalidStatus"));
    }
    StockDTO stock =
        stockMapper
            .findByExternalId(stockExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));

    ItemDTO item =
        itemMapper
            .findById(stock.getItemId())
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.item.notFound")));
    verifyItemOwner(item, user);

    String newMemo = appendMemo(stock.getMemo(), memo);
    int updated =
        stockMapper.updateStatusAndMemoIfInStock(stockExternalId, user.getId(), status, newMemo);
    if (updated != 1) {
      log.warn(
          "stock status change rejected userId={} stockExternalId={} reason=not_in_stock",
          user.getId(),
          stockExternalId);
      throw new NoSuchElementException(getMsg("error.stock.notFound"));
    }

    transactionRecorder.recordStatusChange(
        stock.getId(),
        status == StockStatus.DISPATCHED ? TransactionType.OUT : TransactionType.ADJUST,
        (memo != null && !memo.isBlank()) ? memo.strip() : status.getLabel());

    log.info(
        "stock status changed userId={} stockExternalId={} status={}",
        user.getId(),
        stockExternalId,
        status);
  }

  private String appendMemo(String existing, String reason) {
    if (reason == null || reason.isBlank()) {
      return existing;
    }
    String line = "[" + LocalDate.now() + "] " + reason.strip();
    if (existing == null || existing.isBlank()) {
      return line;
    }
    return existing + "\n" + line;
  }

  @Transactional
  public void moveUnits(StockMoveForm form, String username) {
    UserDTO user = getUser(username);
    VerifiedStockLocation source =
        locationVerifier.resolve(
            form.getSourceSpaceExternalId(),
            form.getSourceShelfExternalId(),
            form.getSourceBoxExternalId(),
            user);
    VerifiedStockLocation target =
        locationVerifier.resolve(
            form.getTargetSpaceExternalId(),
            form.getTargetShelfExternalId(),
            form.getTargetBoxExternalId(),
            user);

    if (locationVerifier.isSameLocation(source, target)) {
      log.warn(
          "stock move rejected userId={} reason=same_location spaceId={} shelfId={} boxId={}",
          user.getId(),
          source.space().getId(),
          source.shelfId(),
          source.boxId());
      throw new IllegalArgumentException(getMsg("error.stock.move.sameLocation"));
    }

    int movedUnitCount = 0;
    for (StockMoveForm.Item moveItem : form.getItems()) {
      ItemDTO item = getVerifiedItem(moveItem.getItemExternalId(), user);
      List<StockDTO> candidates = findInStockUnits(item.getId(), source);
      if (candidates.size() < moveItem.getCount()) {
        log.warn(
            "stock move rejected userId={} itemId={} requested={} available={}",
            user.getId(),
            item.getId(),
            moveItem.getCount(),
            candidates.size());
        throw new IllegalArgumentException(
            item.getName() + " " + getMsg("error.stock.insufficient", candidates.size()));
      }

      List<StockDTO> selected = candidates.subList(0, moveItem.getCount());
      List<Long> stockIds = selected.stream().map(StockDTO::getId).toList();
      int updated =
          stockMapper.updateLocationIfInStock(
              stockIds, target.space().getId(), target.shelfId(), target.boxId());
      if (updated != stockIds.size()) {
        log.warn(
            "stock move rejected userId={} itemId={} reason=status_changed requested={} updated={}",
            user.getId(),
            item.getId(),
            stockIds.size(),
            updated);
        throw new IllegalStateException(getMsg("error.stock.statusChanged"));
      }
      movedUnitCount += updated;

      for (StockDTO unit : selected) {
        transactionRecorder.recordMove(unit, source, target, form.getMemo());
      }
    }
    log.info(
        "stock units moved userId={} sourceSpaceId={} sourceShelfId={} sourceBoxId={} targetSpaceId={} targetShelfId={} targetBoxId={} itemCount={} unitCount={}",
        user.getId(),
        source.space().getId(),
        source.shelfId(),
        source.boxId(),
        target.space().getId(),
        target.shelfId(),
        target.boxId(),
        form.getItems().size(),
        movedUnitCount);
  }

  @Transactional
  public void deleteUnits(
      UUID itemExternalId,
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      String username) {
    UserDTO user = getUser(username);
    ItemDTO item = getVerifiedItem(itemExternalId, user);
    VerifiedStockLocation location =
        locationVerifier.resolve(spaceExternalId, shelfExternalId, boxExternalId, user);

    if (location.box() != null) {
      stockMapper.deleteInStockByItemAndBox(item.getId(), location.box().getId());
    } else if (location.shelf() != null) {
      stockMapper.deleteInStockByItemAndShelf(item.getId(), location.shelf().getId());
    } else {
      stockMapper.deleteInStockByItemAndSpace(item.getId(), location.space().getId());
    }
    log.info(
        "stock units deleted userId={} itemId={} spaceId={} shelfId={} boxId={}",
        user.getId(),
        item.getId(),
        location.space().getId(),
        location.shelfId(),
        location.boxId());
  }

  @Transactional
  public void deleteUnit(UUID stockExternalId, String username) {
    UserDTO user = getUser(username);
    int deleted = stockMapper.deleteInStockByExternalIdAndUserId(stockExternalId, user.getId());
    if (deleted != 1) {
      log.warn(
          "stock unit delete rejected userId={} stockExternalId={} reason=not_found",
          user.getId(),
          stockExternalId);
      throw new NoSuchElementException(getMsg("error.stock.notFound"));
    }
    log.info("stock unit deleted userId={} stockExternalId={}", user.getId(), stockExternalId);
  }

  private ItemDTO getVerifiedItem(UUID itemExternalId, UserDTO user) {
    ItemDTO item =
        itemMapper
            .findByExternalId(itemExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.item.notFound")));
    verifyItemOwner(item, user);
    if (!item.isActive()) {
      log.warn(
          "stock operation rejected userId={} itemId={} reason=item_inactive",
          user.getId(),
          item.getId());
      throw new IllegalStateException(getMsg("error.item.inactive"));
    }
    return item;
  }

  private void verifyItemOwner(ItemDTO item, UserDTO user) {
    if (!item.getUserId().equals(user.getId())) {
      log.warn("access denied userId={} resource=item resourceId={}", user.getId(), item.getId());
      throw new SecurityException(getMsg("error.403.title"));
    }
  }

  private List<StockDTO> findInStockUnits(Long itemId, VerifiedStockLocation location) {
    if (location.box() != null) {
      return stockMapper.findInStockByItemAndBox(itemId, location.box().getId());
    }
    if (location.shelf() != null) {
      return stockMapper.findInStockByItemAndShelf(itemId, location.shelf().getId());
    }
    return stockMapper.findInStockByItemAndSpace(itemId, location.space().getId());
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  private String normalizeSearchType(String searchType) {
    if (searchType == null || searchType.isBlank()) {
      return "all";
    }
    return switch (searchType) {
      case "item", "serial", "lot", "memo" -> searchType;
      default -> "all";
    };
  }

  private String normalizeSort(String sortBy) {
    return sortBy == null || sortBy.isBlank() ? "newest" : sortBy;
  }

  private void attachPrimaryImageIfPresent(Long itemId, UserDTO user, QuickStockForm form) {
    ImageDTO image = imageStorageService.store(form.getImageFile(), user, form.getImageHash());
    if (image == null) {
      return;
    }
    itemImageMapper.insertItemImage(itemId, image.getId(), 0, true);
    log.info(
        "item primary image attached userId={} itemId={} imageId={}",
        user.getId(),
        itemId,
        image.getId());
  }

  private void normalize(StockUpdateForm form) {
    form.setSerialNumber(blankToNull(form.getSerialNumber()));
    form.setLotNumber(blankToNull(form.getLotNumber()));
    form.setMemo(blankToNull(form.getMemo()));
  }
}
