package com.seu.seustock.service;

import com.seu.seustock.mapper.*;
import com.seu.seustock.model.dto.*;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TrackingMode;
import com.seu.seustock.model.enumeration.TransactionMemoMaster;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.model.form.QuickStockForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.model.form.StockInOutForm;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.model.form.StockUpdateForm;
import com.seu.seustock.model.pagination.PageRequest;
import com.seu.seustock.model.pagination.PageResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

  private final StockMapper stockMapper;
  private final StockTransactionMapper transactionMapper;
  private final ItemMapper itemMapper;
  private final ItemLotMapper itemLotMapper;
  private final ItemImageMapper itemImageMapper;
  private final SpaceMapper spaceMapper;
  private final ShelfMapper shelfMapper;
  private final BoxMapper boxMapper;
  private final UserMapper userMapper;
  private final ImageStorageService imageStorageService;
  private final SerialNumberGenerator serialNumberGenerator;
  private final LotNumberGenerator lotNumberGenerator;
  private final Clock clock;
  private final MessageSource messageSource;
  private static final int MEMO_SUGGESTION_LIMIT = 4;
  private static final int MAX_INBOUND_COUNT = 50;

  private record VerifiedLocation(SpaceDTO space, ShelfDTO shelf, BoxDTO box) {
    Long shelfId() {
      return shelf == null ? null : shelf.getId();
    }

    Long boxId() {
      return box == null ? null : box.getId();
    }
  }

  private record LotResolution(Long lotId, String lotNumber, LocalDate expirationDate) {}

  private record InboundSpec(
      int count,
      String serialNumber,
      String serialNumbersText,
      String lotNumber,
      LocalDate expirationDate,
      java.math.BigDecimal price,
      String memo) {}

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  public List<StockPanelDTO> findPanelBySpace(UUID spaceExternalId, String username) {
    return findPanelPageBySpace(spaceExternalId, username, 1).content();
  }

  public PageResult<StockPanelDTO> findPanelPageBySpace(
      UUID spaceExternalId, String username, Integer page) {
    UserDTO user = getUser(username);
    SpaceDTO space = getVerifiedSpace(spaceExternalId, user);
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
    SpaceDTO space = getVerifiedSpace(spaceExternalId, user);
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
    SpaceDTO space = getVerifiedSpace(spaceExternalId, user);
    ShelfDTO shelf =
        shelfMapper
            .findByExternalId(shelfExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
    if (!shelf.getSpaceId().equals(space.getId())) {
      throw new SecurityException(getMsg("error.403.title"));
    }
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
    SpaceDTO space = getVerifiedSpace(spaceExternalId, user);
    ShelfDTO shelf =
        shelfMapper
            .findByExternalId(shelfExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.shelf.notFound")));
    if (!shelf.getSpaceId().equals(space.getId())) {
      throw new SecurityException(getMsg("error.403.title"));
    }
    BoxDTO box =
        boxMapper
            .findByExternalId(boxExternalId)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.box.notFound")));
    if (!box.getShelfId().equals(shelf.getId())) {
      throw new SecurityException(getMsg("error.403.title"));
    }
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
      String keyword,
      String searchType,
      String sortBy,
      String username) {
    return searchDetailsPage(
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
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
      String keyword,
      String searchType,
      String sortBy,
      String username,
      Integer page) {
    UserDTO user = getUser(username);
    String effectiveKeyword = normalizeKeyword(keyword);
    String effectiveSearchType = normalizeSearchType(searchType);
    int totalCount =
        stockMapper.countSearchDetails(
            user.getId(),
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
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
    VerifiedLocation location =
        resolveVerifiedLocation(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        prepareInboundUnits(
            item,
            location,
            new InboundSpec(
                form.getCount(),
                form.getSerialNumber(),
                form.getSerialNumbersText(),
                form.getLotNumber(),
                form.getExpirationDate(),
                form.getPrice(),
                form.getMemo()));

    String memo = form.getMemo() != null ? form.getMemo() : getMsg("stock.memo.initial");
    insertInboundTransactions(units, memo);
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

    VerifiedLocation location =
        resolveVerifiedLocation(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        prepareInboundUnits(
            item,
            location,
            new InboundSpec(form.getCount(), null, null, null, null, null, form.getMemo()));

    String memo = form.getMemo() != null ? form.getMemo() : getMsg("stock.memo.quick");
    insertInboundTransactions(units, memo);
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
    VerifiedLocation location =
        resolveVerifiedLocation(
            form.getSpaceExternalId(), form.getShelfExternalId(), form.getBoxExternalId(), user);

    List<StockDTO> units =
        prepareInboundUnits(
            item,
            location,
            new InboundSpec(
                form.getCount(),
                null,
                form.getSerialNumbersText(),
                form.getLotNumber(),
                form.getExpirationDate(),
                form.getPrice(),
                form.getMemo()));

    insertInboundTransactions(units, form.getMemo());
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
    VerifiedLocation location =
        resolveVerifiedLocation(
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

      StockTransactionDTO tx = new StockTransactionDTO();
      tx.setStockId(unit.getId());
      tx.setTransactionType(TransactionType.OUT);
      tx.setMemo(form.getMemo());
      transactionMapper.insertTransaction(tx);
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

    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(stock.getId());
    tx.setTransactionType(TransactionType.ADJUST);
    tx.setMemo(kept ? "보관 설정" : "보관 해제");
    transactionMapper.insertTransaction(tx);

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

    StockTransactionDTO tx = new StockTransactionDTO();
    tx.setStockId(stock.getId());
    tx.setTransactionType(
        status == StockStatus.DISPATCHED ? TransactionType.OUT : TransactionType.ADJUST);
    tx.setMemo((memo != null && !memo.isBlank()) ? memo.strip() : status.getLabel());
    transactionMapper.insertTransaction(tx);

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
    VerifiedLocation source =
        resolveVerifiedLocation(
            form.getSourceSpaceExternalId(),
            form.getSourceShelfExternalId(),
            form.getSourceBoxExternalId(),
            user);
    VerifiedLocation target =
        resolveVerifiedLocation(
            form.getTargetSpaceExternalId(),
            form.getTargetShelfExternalId(),
            form.getTargetBoxExternalId(),
            user);

    if (isSameLocation(source, target)) {
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
        StockTransactionDTO tx = new StockTransactionDTO();
        tx.setStockId(unit.getId());
        tx.setTransactionType(TransactionType.MOVE);
        tx.setFromSpaceId(source.space().getId());
        tx.setFromShelfId(source.shelfId());
        tx.setFromBoxId(source.boxId());
        tx.setToSpaceId(target.space().getId());
        tx.setToShelfId(target.shelfId());
        tx.setToBoxId(target.boxId());
        tx.setMemo(form.getMemo());
        transactionMapper.insertTransaction(tx);
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
    VerifiedLocation location =
        resolveVerifiedLocation(spaceExternalId, shelfExternalId, boxExternalId, user);

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

  private VerifiedLocation resolveVerifiedLocation(
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

    return new VerifiedLocation(space, shelf, box);
  }

  private List<StockDTO> findInStockUnits(Long itemId, VerifiedLocation location) {
    if (location.box() != null) {
      return stockMapper.findInStockByItemAndBox(itemId, location.box().getId());
    }
    if (location.shelf() != null) {
      return stockMapper.findInStockByItemAndShelf(itemId, location.shelf().getId());
    }
    return stockMapper.findInStockByItemAndSpace(itemId, location.space().getId());
  }

  private List<StockDTO> prepareInboundUnits(
      ItemDTO item, VerifiedLocation location, InboundSpec spec) {
    List<String> serialNumbers = resolveSerialNumbers(item, spec);
    TrackingMode serialMode =
        item.getSerialMode() == null ? TrackingMode.NONE : item.getSerialMode();
    int count = serialMode == TrackingMode.MANUAL ? serialNumbers.size() : spec.count();
    validateInboundCount(count);
    LotResolution lot = resolveLot(item, spec);
    List<StockDTO> units = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      StockDTO unit = new StockDTO();
      unit.setItemId(item.getId());
      unit.setSpaceId(location.space().getId());
      unit.setShelfId(location.shelfId());
      unit.setBoxId(location.boxId());
      unit.setLotId(lot.lotId());
      unit.setLotNumber(lot.lotNumber());
      unit.setExpirationDate(lot.expirationDate());
      unit.setSerialNumber(serialNumbers.get(i));
      unit.setPrice(spec.price());
      unit.setMemo(spec.memo());
      units.add(unit);
    }
    stockMapper.insertStocks(units);
    return units;
  }

  private List<String> resolveSerialNumbers(ItemDTO item, InboundSpec spec) {
    TrackingMode mode = item.getSerialMode() == null ? TrackingMode.NONE : item.getSerialMode();
    if (mode == TrackingMode.NONE) {
      List<String> serialNumbers = new ArrayList<>(spec.count());
      String singleSerial = blankToNull(spec.serialNumber());
      for (int i = 0; i < spec.count(); i++) {
        serialNumbers.add(spec.count() == 1 ? singleSerial : null);
      }
      if (singleSerial != null) {
        rejectExistingSerials(item.getId(), List.of(singleSerial));
      }
      return serialNumbers;
    }
    if (mode == TrackingMode.MANUAL) {
      List<String> serialNumbers = parseManualSerials(spec.serialNumbersText());
      validateInboundCount(serialNumbers.size());
      rejectDuplicateSerials(serialNumbers);
      rejectExistingSerials(item.getId(), serialNumbers);
      return serialNumbers;
    }
    validateInboundCount(spec.count());
    SerialNumberGenerator.Result result =
        serialNumberGenerator.generate(
            item.getSerialPrefix(),
            item.getSerialPaddingLength(),
            item.getSerialIncrementUnit(),
            item.getSerialNextSequence(),
            spec.count());
    rejectExistingSerials(item.getId(), result.serialNumbers());
    itemMapper.updateSerialNextSequence(item.getId(), result.nextSequence());
    return result.serialNumbers();
  }

  private LotResolution resolveLot(ItemDTO item, InboundSpec spec) {
    TrackingMode mode = item.getLotMode() == null ? TrackingMode.NONE : item.getLotMode();
    if (mode == TrackingMode.NONE) {
      String legacyLotNumber = blankToNull(spec.lotNumber());
      LocalDate expirationDate = resolveExpirationDate(item, spec.expirationDate());
      return new LotResolution(null, legacyLotNumber, expirationDate);
    }
    String lotNumber;
    if (mode == TrackingMode.AUTO) {
      LotNumberGenerator.Result generated = lotNumberGenerator.generate(item, LocalDate.now(clock));
      lotNumber = generated.lotNumber();
      itemMapper.updateLotSequence(item.getId(), generated.sequenceKey(), generated.nextSequence());
    } else {
      lotNumber = blankToNull(spec.lotNumber());
      if (lotNumber == null) {
        throw new IllegalArgumentException(getMsg("error.lot.numberRequired"));
      }
    }
    ItemLotDTO lot =
        itemLotMapper
            .findByItemIdAndLotNumber(item.getId(), lotNumber)
            .orElseGet(() -> createLot(item, lotNumber, spec.expirationDate()));
    return new LotResolution(lot.getId(), lot.getLotNumber(), lot.getExpirationDate());
  }

  private ItemLotDTO createLot(ItemDTO item, String lotNumber, LocalDate formExpirationDate) {
    ItemLotDTO lot = new ItemLotDTO();
    lot.setItemId(item.getId());
    lot.setLotNumber(lotNumber);
    lot.setExpirationDate(resolveExpirationDate(item, formExpirationDate));
    itemLotMapper.insertLot(lot);
    return itemLotMapper.findById(lot.getId()).orElse(lot);
  }

  private LocalDate resolveExpirationDate(ItemDTO item, LocalDate formExpirationDate) {
    if (item.getExpirationPeriodDays() != null) {
      return LocalDate.now(clock).plusDays(item.getExpirationPeriodDays());
    }
    return formExpirationDate;
  }

  private List<String> parseManualSerials(String serialNumbersText) {
    if (serialNumbersText == null || serialNumbersText.isBlank()) {
      throw new IllegalArgumentException(getMsg("error.serial.required"));
    }
    return serialNumbersText.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
  }

  private void validateInboundCount(int count) {
    if (count < 1 || count > MAX_INBOUND_COUNT) {
      throw new IllegalArgumentException(getMsg("error.stock.countRange", MAX_INBOUND_COUNT));
    }
  }

  private void rejectDuplicateSerials(List<String> serialNumbers) {
    Set<String> seen = new HashSet<>();
    for (String serialNumber : serialNumbers) {
      if (!seen.add(serialNumber)) {
        throw new IllegalArgumentException(getMsg("error.serial.duplicate", serialNumber));
      }
    }
  }

  private void rejectExistingSerials(Long itemId, List<String> serialNumbers) {
    List<String> nonBlankSerials =
        serialNumbers.stream()
            .filter(Objects::nonNull)
            .filter(serial -> !serial.isBlank())
            .toList();
    if (nonBlankSerials.isEmpty()) {
      return;
    }
    List<String> existing = stockMapper.findExistingSerialNumbers(itemId, nonBlankSerials);
    if (!existing.isEmpty()) {
      throw new IllegalArgumentException(getMsg("error.serial.exists", existing.get(0)));
    }
  }

  private void insertInboundTransactions(List<StockDTO> units, String memo) {
    List<StockTransactionDTO> txs = new ArrayList<>(units.size());
    for (StockDTO unit : units) {
      StockTransactionDTO tx = new StockTransactionDTO();
      tx.setStockId(unit.getId());
      tx.setTransactionType(TransactionType.IN);
      tx.setMemo(memo);
      txs.add(tx);
    }
    transactionMapper.insertTransactions(txs);
  }

  private boolean isSameLocation(VerifiedLocation source, VerifiedLocation target) {
    return Objects.equals(source.space().getId(), target.space().getId())
        && Objects.equals(source.shelfId(), target.shelfId())
        && Objects.equals(source.boxId(), target.boxId());
  }

  private SpaceDTO getVerifiedSpace(UUID spaceExternalId, UserDTO user) {
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

  private UserDTO getUser(String username) {
    return userMapper
        .findByEmail(username)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
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

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
