package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDetailDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.model.form.QuickStockForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.model.form.StockInOutForm;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.model.form.StockUpdateForm;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ItemService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StockController {

  private final StockService stockService;
  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final BoxService boxService;
  private final ItemService itemService;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /* ── 내 재고 페이지 ── */

  @GetMapping("/stocks")
  public String list(
      @RequestParam(name = "item", required = false) UUID itemExternalId,
      @RequestParam(name = "space", required = false) UUID spaceExternalId,
      @RequestParam(name = "shelf", required = false) UUID shelfExternalId,
      @RequestParam(name = "box", required = false) UUID boxExternalId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "all") String searchType,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var stocksPage =
        stockService.searchDetailsPage(
            itemExternalId,
            spaceExternalId,
            shelfExternalId,
            boxExternalId,
            keyword,
            searchType,
            sortBy,
            username,
            page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("item", itemExternalId);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("keyword", keyword);
    model.addAttribute("searchType", searchType);
    model.addAttribute("sortBy", sortBy);
    model.addAttribute("activeNav", "stocks");
    model.addAttribute(
        "filtered",
        itemExternalId != null
            || spaceExternalId != null
            || shelfExternalId != null
            || boxExternalId != null
            || (keyword != null && !keyword.isBlank()));
    return "stocks/list";
  }

  @GetMapping("/stocks/{stockExternalId}/edit")
  public String editRow(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    StockUpdateForm form = new StockUpdateForm();
    var stock = stockService.findDetailByExternalId(stockExternalId, username);
    form.setSerialNumber(stock.getSerialNumber());
    form.setLotNumber(stock.getLotNumber());
    form.setExpirationDate(stock.getExpirationDate());
    form.setPrice(stock.getPrice());
    form.setMemo(stock.getMemo());
    model.addAttribute("stock", stock);
    model.addAttribute("form", form);
    return "stocks/fragments/detail-row :: edit";
  }

  @PutMapping("/stocks/{stockExternalId}")
  public String updateRow(
      @PathVariable UUID stockExternalId,
      @Valid @ModelAttribute("form") StockUpdateForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    var stock = stockService.findDetailByExternalId(stockExternalId, username);
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.update stockExternalId={} errorCount={} fields={}",
          stockExternalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("stock", stock);
      return "stocks/fragments/detail-row :: edit";
    }
    form.setMemo(stock.getMemo());
    model.addAttribute("stock", stockService.updateDetails(stockExternalId, form, username));
    HtmxResponse.success(response, getMsg("toast.stock.updated"));
    return "stocks/fragments/detail-row :: view";
  }

  @GetMapping("/stocks/{stockExternalId}/cancel")
  public String cancelEdit(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("stock", stockService.findDetailByExternalId(stockExternalId, username));
    return "stocks/fragments/detail-row :: view";
  }

  @PostMapping("/stocks/{stockExternalId}/keep")
  public String toggleKeep(
      @PathVariable UUID stockExternalId,
      @RequestParam boolean kept,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    StockDetailDTO stock = stockService.setKeepStatus(stockExternalId, kept, username);
    model.addAttribute("stock", stock);
    HtmxResponse.success(response, getMsg(kept ? "toast.stock.kept" : "toast.stock.unkept"));
    return "stocks/fragments/detail-row :: view";
  }

  @GetMapping("/stocks/{stockExternalId}/status")
  public String statusModal(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("stock", stockService.findDetailByExternalId(stockExternalId, username));
    model.addAttribute(
        "statuses",
        Arrays.stream(StockStatus.values()).filter(s -> s != StockStatus.IN_STOCK).toList());
    return "stocks/fragments/status-modal :: modal";
  }

  @PutMapping("/stocks/{stockExternalId}/status")
  public String changeStatus(
      @PathVariable UUID stockExternalId,
      @RequestParam StockStatus status,
      @RequestParam(required = false) String memo,
      Principal principal,
      HttpServletResponse response) {
    String username = principal.getName();
    stockService.changeStatus(stockExternalId, status, memo, username);
    HtmxResponse.success(response, getMsg("toast.stock.statusChanged"));
    return "stocks/fragments/status-modal :: removed";
  }

  @GetMapping("/stocks/{stockExternalId}/memo")
  public String viewMemo(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    var stock = stockService.findDetailByExternalId(stockExternalId, username);
    model.addAttribute("stock", stock);
    return "stocks/fragments/memo-modal :: view";
  }

  @GetMapping("/stocks/{stockExternalId}/memo/edit")
  public String editMemo(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    var stock = stockService.findDetailByExternalId(stockExternalId, username);
    model.addAttribute("stock", stock);
    return "stocks/fragments/memo-modal :: edit";
  }

  @PutMapping("/stocks/{stockExternalId}/memo")
  public String updateMemo(
      @PathVariable UUID stockExternalId,
      @RequestParam(required = false) String memo,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    var stock = stockService.findDetailByExternalId(stockExternalId, username);

    StockUpdateForm form = new StockUpdateForm();
    form.setSerialNumber(stock.getSerialNumber());
    form.setLotNumber(stock.getLotNumber());
    form.setExpirationDate(stock.getExpirationDate());
    form.setPrice(stock.getPrice());
    form.setMemo(memo);

    var updated = stockService.updateDetails(stockExternalId, form, username);
    model.addAttribute("stock", updated);

    HtmxResponse.success(response, getMsg("toast.stock.updated"));
    return "stocks/fragments/detail-row :: view-with-modal-close";
  }

  @GetMapping("/stocks/{stockExternalId}/history")
  public String history(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("stock", stockService.findDetailByExternalId(stockExternalId, username));
    model.addAttribute("history", stockService.findUnitHistory(stockExternalId, username));
    return "stocks/fragments/history-modal :: modal";
  }

  /* ── 재고 패널 조회 ── */

  @GetMapping("/spaces/{spaceExternalId}/stocks/all")
  public String panelBySpaceAll(
      @PathVariable UUID spaceExternalId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
    var stocksPage =
        stockService.findPanelPageBySpaceAll(spaceExternalId, keyword, sortBy, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", getMsg("view.stock.breadcrumb.all", space.getName()));
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("isAllView", true);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/stocks")
  public String panelBySpace(
      @PathVariable UUID spaceExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
    var stocksPage = stockService.findPanelPageBySpace(spaceExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", getMsg("view.stock.breadcrumb.loose", space.getName()));
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/stocks")
  public String panelByShelf(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    ShelfDTO shelf = shelfService.findByExternalId(spaceExternalId, shelfExternalId, username);
    var stocksPage =
        stockService.findPanelPageByShelf(spaceExternalId, shelfExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", shelf.getName());
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/{boxExternalId}/stocks")
  public String panelByBox(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @PathVariable UUID boxExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var stocksPage =
        stockService.findPanelPageByBox(
            spaceExternalId, shelfExternalId, boxExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute(
        "breadcrumb",
        boxService
            .findByExternalId(spaceExternalId, shelfExternalId, boxExternalId, username)
            .getName());
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  /* ── 재고 등록 ── */

  @GetMapping("/stocks/new")
  public String newModal(
      @RequestParam UUID spaceId,
      @RequestParam(required = false) UUID shelfId,
      @RequestParam(required = false) UUID boxId,
      Principal principal,
      Model model) {
    String username = principal.getName();
    model.addAttribute("items", itemService.findAllByUsername(username));
    model.addAttribute("spaceId", spaceId);
    model.addAttribute("shelfId", shelfId);
    model.addAttribute("boxId", boxId);
    model.addAttribute("form", new StockForm());
    return "stocks/fragments/modal :: modal";
  }

  @PostMapping("/stocks")
  public String create(
      @Valid @ModelAttribute("form") StockForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.create spaceExternalId={} shelfExternalId={} boxExternalId={} errorCount={} fields={}",
          form.getSpaceExternalId(),
          form.getShelfExternalId(),
          form.getBoxExternalId(),
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("items", itemService.findAllByUsername(username));
      model.addAttribute("spaceId", form.getSpaceExternalId());
      model.addAttribute("shelfId", form.getShelfExternalId());
      model.addAttribute("boxId", form.getBoxExternalId());
      return "stocks/fragments/modal :: modal";
    }
    stockService.create(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.created"));
    return buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        username,
        model);
  }

  /* ── 빠른 등록 (품목+재고 동시 생성) ── */

  @GetMapping("/stocks/quick")
  public String quickModal(
      @RequestParam UUID spaceId,
      @RequestParam(required = false) UUID shelfId,
      @RequestParam(required = false) UUID boxId,
      Model model) {
    model.addAttribute("spaceId", spaceId);
    model.addAttribute("shelfId", shelfId);
    model.addAttribute("boxId", boxId);
    model.addAttribute("form", new QuickStockForm());
    return "stocks/fragments/quick-modal :: modal";
  }

  @PostMapping("/stocks/quick")
  public String createQuick(
      @Valid @ModelAttribute("form") QuickStockForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.quickCreate spaceExternalId={} shelfExternalId={} boxExternalId={} errorCount={} fields={}",
          form.getSpaceExternalId(),
          form.getShelfExternalId(),
          form.getBoxExternalId(),
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("spaceId", form.getSpaceExternalId());
      model.addAttribute("shelfId", form.getShelfExternalId());
      model.addAttribute("boxId", form.getBoxExternalId());
      return "stocks/fragments/quick-modal :: modal";
    }
    stockService.createWithNewItem(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.quickCreated"));
    return buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        username,
        model);
  }

  /* ── 재고 삭제 ── */

  @DeleteMapping("/stocks")
  public String delete(
      @RequestParam(name = "item") UUID itemExternalId,
      @RequestParam(name = "space") UUID spaceExternalId,
      @RequestParam(name = "shelf", required = false) UUID shelfExternalId,
      @RequestParam(name = "box", required = false) UUID boxExternalId,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    stockService.deleteUnits(
        itemExternalId, spaceExternalId, shelfExternalId, boxExternalId, username);
    HtmxResponse.success(response, getMsg("toast.stock.deleted"));
    return buildPanelResponse(spaceExternalId, shelfExternalId, boxExternalId, username, model);
  }

  @DeleteMapping("/stocks/{stockExternalId}")
  @ResponseBody
  public String deleteRow(
      @PathVariable UUID stockExternalId, Principal principal, HttpServletResponse response) {
    String username = principal.getName();
    stockService.deleteUnit(stockExternalId, username);
    HtmxResponse.success(response, getMsg("toast.stock.deleted"));
    return "";
  }

  /* ── 통합 액션 모달 ── */

  @GetMapping("/stocks/action-form")
  public String actionForm(
      @RequestParam(name = "item") UUID itemExternalId,
      @RequestParam String itemName,
      @RequestParam(name = "space") UUID spaceExternalId,
      @RequestParam(name = "shelf", required = false) UUID shelfExternalId,
      @RequestParam(name = "box", required = false) UUID boxExternalId,
      @RequestParam(defaultValue = "0") Integer count,
      Principal principal,
      Model model) {
    String username = principal.getName();
    model.addAttribute("itemName", itemName);
    model.addAttribute("item", itemExternalId);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("currentCount", count);
    model.addAttribute(
        "inMemoSuggestions", stockService.findMemoSuggestions(TransactionType.IN, username));
    model.addAttribute(
        "outMemoSuggestions", stockService.findMemoSuggestions(TransactionType.OUT, username));
    return "stocks/fragments/action-modal :: modal";
  }

  /* ── 입고 ── */

  @GetMapping("/stocks/in-form")
  public String inForm(
      @RequestParam(name = "item") UUID itemExternalId,
      @RequestParam(name = "space") UUID spaceExternalId,
      @RequestParam(name = "shelf", required = false) UUID shelfExternalId,
      @RequestParam(name = "box", required = false) UUID boxExternalId,
      Principal principal,
      Model model) {
    StockInOutForm form = new StockInOutForm();
    form.setItemExternalId(itemExternalId);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    var item = itemService.findByExternalId(itemExternalId, principal.getName());
    form.setPrice(item.getPrice());
    model.addAttribute("item", item);
    model.addAttribute("form", form);
    return "stocks/fragments/in-modal :: modal";
  }

  @PostMapping("/stocks/in")
  public String processIn(
      @Valid @ModelAttribute("form") StockInOutForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.in itemExternalId={} spaceExternalId={} shelfExternalId={} boxExternalId={} errorCount={} fields={}",
          form.getItemExternalId(),
          form.getSpaceExternalId(),
          form.getShelfExternalId(),
          form.getBoxExternalId(),
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute(
          "item", itemService.findByExternalId(form.getItemExternalId(), principal.getName()));
      return "stocks/fragments/in-modal :: modal";
    }
    String username = principal.getName();
    stockService.addUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.in"));
    return buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        username,
        model);
  }

  /* ── 출고 ── */

  @GetMapping("/stocks/out-form")
  public String outForm(
      @RequestParam(name = "item") UUID itemExternalId,
      @RequestParam(name = "space") UUID spaceExternalId,
      @RequestParam(name = "shelf", required = false) UUID shelfExternalId,
      @RequestParam(name = "box", required = false) UUID boxExternalId,
      Model model) {
    StockInOutForm form = new StockInOutForm();
    form.setItemExternalId(itemExternalId);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    model.addAttribute("form", form);
    return "stocks/fragments/out-modal :: modal";
  }

  @PostMapping("/stocks/out")
  public String processOut(
      @Valid @ModelAttribute("form") StockInOutForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.out itemExternalId={} spaceExternalId={} shelfExternalId={} boxExternalId={} errorCount={} fields={}",
          form.getItemExternalId(),
          form.getSpaceExternalId(),
          form.getShelfExternalId(),
          form.getBoxExternalId(),
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      return "stocks/fragments/out-modal :: modal";
    }
    String username = principal.getName();
    stockService.dispatchUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.out"));
    return buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        username,
        model);
  }

  /* ── 이동 ── */

  @GetMapping("/stocks/move-form")
  public String moveForm(
      @ModelAttribute("form") StockMoveForm form, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("locationOptions", buildMoveLocationOptions(form, username));
    return "stocks/fragments/move-modal :: modal";
  }

  @PostMapping("/stocks/move")
  public String processMove(
      @Valid @ModelAttribute("form") StockMoveForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=stock.move sourceSpaceExternalId={} sourceShelfExternalId={} sourceBoxExternalId={} targetSpaceExternalId={} targetShelfExternalId={} targetBoxExternalId={} errorCount={} fields={}",
          form.getSourceSpaceExternalId(),
          form.getSourceShelfExternalId(),
          form.getSourceBoxExternalId(),
          form.getTargetSpaceExternalId(),
          form.getTargetShelfExternalId(),
          form.getTargetBoxExternalId(),
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("locationOptions", buildMoveLocationOptions(form, username));
      return "stocks/fragments/move-modal :: modal";
    }
    stockService.moveUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.moved"));
    return buildPanelResponse(
        form.getSourceSpaceExternalId(),
        form.getSourceShelfExternalId(),
        form.getSourceBoxExternalId(),
        username,
        model);
  }

  private String buildPanelResponse(
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      String username,
      Model model) {
    String breadcrumb;
    var page =
        boxExternalId != null
            ? stockService.findPanelPageByBox(
                spaceExternalId, shelfExternalId, boxExternalId, username, 1)
            : shelfExternalId != null
                ? stockService.findPanelPageByShelf(spaceExternalId, shelfExternalId, username, 1)
                : stockService.findPanelPageBySpace(spaceExternalId, username, 1);
    if (boxExternalId != null) {
      breadcrumb =
          boxService
              .findByExternalId(spaceExternalId, shelfExternalId, boxExternalId, username)
              .getName();
    } else if (shelfExternalId != null) {
      breadcrumb =
          shelfService.findByExternalId(spaceExternalId, shelfExternalId, username).getName();
    } else {
      SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
      breadcrumb = getMsg("view.stock.breadcrumb.loose", space.getName());
    }
    model.addAttribute("stocks", page.content());
    model.addAttribute("page", page);
    model.addAttribute("breadcrumb", breadcrumb);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("isAllView", false);
    return "stocks/fragments/panel :: stock-panel-response";
  }

  private List<MoveLocationOption> buildMoveLocationOptions(StockMoveForm form, String username) {
    List<MoveLocationOption> options = new java.util.ArrayList<>();
    UUID spaceExternalId = form.getSourceSpaceExternalId();
    String spaceName = spaceService.findByExternalId(spaceExternalId, username).getName();
    options.add(
        new MoveLocationOption(
            getMsg("view.stock.breadcrumb.loose", spaceName),
            spaceExternalId,
            null,
            null,
            isSameLocation(form, spaceExternalId, null, null)));

    for (var shelf : shelfService.findAllBySpaceId(spaceExternalId, username)) {
      options.add(
          new MoveLocationOption(
              shelf.getName(),
              spaceExternalId,
              shelf.getExternalId(),
              null,
              isSameLocation(form, spaceExternalId, shelf.getExternalId(), null)));

      for (var box :
          boxService.findAllByShelfId(spaceExternalId, shelf.getExternalId(), username)) {
        options.add(
            new MoveLocationOption(
                shelf.getName() + " / " + box.getName(),
                spaceExternalId,
                shelf.getExternalId(),
                box.getExternalId(),
                isSameLocation(form, spaceExternalId, shelf.getExternalId(), box.getExternalId())));
      }
    }
    return options;
  }

  private boolean isSameLocation(
      StockMoveForm form, UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId) {
    return Objects.equals(form.getSourceSpaceExternalId(), spaceExternalId)
        && Objects.equals(form.getSourceShelfExternalId(), shelfExternalId)
        && Objects.equals(form.getSourceBoxExternalId(), boxExternalId);
  }

  public record MoveLocationOption(
      String label,
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      boolean current) {}
}
