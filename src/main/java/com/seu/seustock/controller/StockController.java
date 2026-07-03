package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.dto.StockDetailDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.form.StockUpdateForm;
import com.seu.seustock.service.StockService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/** 내 재고 목록과 개별 재고 행(상세/메모/상태/히스토리) 조회·수정을 담당하는 컨트롤러. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StockController {

  private final StockService stockService;
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

  /* ── 재고 상세 행 ── */

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

  /* ── 상태 변경 ── */

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

  /* ── 메모 ── */

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

  /* ── 히스토리 ── */

  @GetMapping("/stocks/{stockExternalId}/history")
  public String history(@PathVariable UUID stockExternalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("stock", stockService.findDetailByExternalId(stockExternalId, username));
    model.addAttribute("history", stockService.findUnitHistory(stockExternalId, username));
    return "stocks/fragments/history-modal :: modal";
  }
}
