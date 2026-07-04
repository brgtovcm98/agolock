package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.controller.support.StockPanelHelper;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.model.form.QuickStockForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.model.form.StockInOutForm;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ItemService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/** 재고 등록·삭제·입고·출고·이동 등 상태를 변경하는 재고 조작 컨트롤러. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StockOperationController {

  private final StockService stockService;
  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final BoxService boxService;
  private final ItemService itemService;
  private final MessageSource messageSource;
  private final StockPanelHelper stockPanelHelper;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /* ── 재고 등록 ── */

  @GetMapping("/stocks/new")
  public String newModal(
      @RequestParam UUID spaceId,
      @RequestParam(required = false) UUID shelfId,
      @RequestParam(required = false) UUID boxId,
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Principal principal,
      Model model) {
    String username = principal.getName();
    model.addAttribute("items", itemService.findAllByUsername(username));
    model.addAttribute("spaceId", spaceId);
    model.addAttribute("shelfId", shelfId);
    model.addAttribute("boxId", boxId);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    StockForm form = new StockForm();
    form.setAllView(allView);
    form.setKeyword(keyword);
    form.setSortBy(sortBy);
    model.addAttribute("form", form);
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
      model.addAttribute("allView", form.isAllView());
      model.addAttribute("keyword", form.getKeyword());
      model.addAttribute("sortBy", form.getSortBy());
      return "stocks/fragments/modal :: modal";
    }
    stockService.create(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.created"));
    return stockPanelHelper.buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        form.isAllView(),
        form.getKeyword(),
        form.getSortBy(),
        username,
        model);
  }

  /* ── 빠른 등록 (품목+재고 동시 생성) ── */

  @GetMapping("/stocks/quick")
  public String quickModal(
      @RequestParam UUID spaceId,
      @RequestParam(required = false) UUID shelfId,
      @RequestParam(required = false) UUID boxId,
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Model model) {
    model.addAttribute("spaceId", spaceId);
    model.addAttribute("shelfId", shelfId);
    model.addAttribute("boxId", boxId);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    QuickStockForm form = new QuickStockForm();
    form.setAllView(allView);
    form.setKeyword(keyword);
    form.setSortBy(sortBy);
    model.addAttribute("form", form);
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
      model.addAttribute("allView", form.isAllView());
      model.addAttribute("keyword", form.getKeyword());
      model.addAttribute("sortBy", form.getSortBy());
      return "stocks/fragments/quick-modal :: modal";
    }
    stockService.createWithNewItem(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.quickCreated"));
    return stockPanelHelper.buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        form.isAllView(),
        form.getKeyword(),
        form.getSortBy(),
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
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    stockService.deleteUnits(
        itemExternalId, spaceExternalId, shelfExternalId, boxExternalId, username);
    HtmxResponse.success(response, getMsg("toast.stock.deleted"));
    return stockPanelHelper.buildPanelResponse(
        spaceExternalId, shelfExternalId, boxExternalId, allView, keyword, sortBy, username, model);
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
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Principal principal,
      Model model) {
    String username = principal.getName();
    model.addAttribute("itemName", itemName);
    model.addAttribute("item", itemExternalId);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("currentCount", count);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
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
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Principal principal,
      Model model) {
    StockInOutForm form = new StockInOutForm();
    form.setItemExternalId(itemExternalId);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    form.setAllView(allView);
    form.setKeyword(keyword);
    form.setSortBy(sortBy);
    var item = itemService.findByExternalId(itemExternalId, principal.getName());
    form.setPrice(item.getPrice());
    model.addAttribute("item", item);
    model.addAttribute("form", form);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
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
      model.addAttribute("allView", form.isAllView());
      model.addAttribute("keyword", form.getKeyword());
      model.addAttribute("sortBy", form.getSortBy());
      return "stocks/fragments/in-modal :: modal";
    }
    String username = principal.getName();
    stockService.addUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.in"));
    return stockPanelHelper.buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        form.isAllView(),
        form.getKeyword(),
        form.getSortBy(),
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
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Model model) {
    StockInOutForm form = new StockInOutForm();
    form.setItemExternalId(itemExternalId);
    form.setSpaceExternalId(spaceExternalId);
    form.setShelfExternalId(shelfExternalId);
    form.setBoxExternalId(boxExternalId);
    form.setAllView(allView);
    form.setKeyword(keyword);
    form.setSortBy(sortBy);
    model.addAttribute("form", form);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
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
      model.addAttribute("allView", form.isAllView());
      model.addAttribute("keyword", form.getKeyword());
      model.addAttribute("sortBy", form.getSortBy());
      return "stocks/fragments/out-modal :: modal";
    }
    String username = principal.getName();
    stockService.dispatchUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.out"));
    return stockPanelHelper.buildPanelResponse(
        form.getSpaceExternalId(),
        form.getShelfExternalId(),
        form.getBoxExternalId(),
        form.isAllView(),
        form.getKeyword(),
        form.getSortBy(),
        username,
        model);
  }

  /* ── 이동 ── */

  @GetMapping("/stocks/move-form")
  public String moveForm(
      @ModelAttribute("form") StockMoveForm form,
      @RequestParam(required = false, defaultValue = "false") boolean allView,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      Principal principal,
      Model model) {
    String username = principal.getName();
    form.setAllView(allView);
    form.setKeyword(keyword);
    form.setSortBy(sortBy);
    model.addAttribute("allView", allView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    model.addAttribute(
        "locationOptions", stockPanelHelper.buildMoveLocationOptions(form, username));
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
      model.addAttribute("allView", form.isAllView());
      model.addAttribute("keyword", form.getKeyword());
      model.addAttribute("sortBy", form.getSortBy());
      model.addAttribute(
          "locationOptions", stockPanelHelper.buildMoveLocationOptions(form, username));
      return "stocks/fragments/move-modal :: modal";
    }
    stockService.moveUnits(form, username);
    HtmxResponse.success(response, getMsg("toast.stock.moved"));
    return stockPanelHelper.buildPanelResponse(
        form.getSourceSpaceExternalId(),
        form.getSourceShelfExternalId(),
        form.getSourceBoxExternalId(),
        form.isAllView(),
        form.getKeyword(),
        form.getSortBy(),
        username,
        model);
  }
}
