package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.form.ItemForm;
import com.seu.seustock.model.form.StockForm;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ItemService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/items")
@RequiredArgsConstructor
@Slf4j
public class ItemController {

  private final ItemService itemService;
  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final BoxService boxService;
  private final StockService stockService;
  private final org.springframework.context.MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(
        key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale());
  }

  @org.springframework.web.bind.annotation.InitBinder
  public void initBinder(org.springframework.web.bind.WebDataBinder binder) {
    if (binder.getConversionService()
        instanceof
        org.springframework.core.convert.support.GenericConversionService conversionService) {
      conversionService.addConverter(
          String[].class,
          UUID.class,
          source ->
              source == null || source.length == 0 || source[0] == null || source[0].isBlank()
                  ? null
                  : UUID.fromString(source[0]));
    }
    binder.registerCustomEditor(
        UUID.class,
        new java.beans.PropertyEditorSupport() {
          @Override
          public void setAsText(String text) {
            setValue(text == null || text.isBlank() ? null : UUID.fromString(text));
          }

          @Override
          public void setValue(Object value) {
            if (value instanceof String[] values && values.length > 0) {
              String first = values[0];
              super.setValue(first == null || first.isBlank() ? null : UUID.fromString(first));
            } else {
              super.setValue(value);
            }
          }
        });
  }

  @GetMapping
  public String list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "name") String searchType,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var itemsPage = itemService.findPageByUsername(username, keyword, searchType, sortBy, page);
    model.addAttribute("items", itemsPage.content());
    model.addAttribute("page", itemsPage);
    model.addAttribute("keyword", keyword);
    model.addAttribute("searchType", searchType);
    model.addAttribute("sortBy", sortBy);
    model.addAttribute("activeNav", "items");
    return "items/list";
  }

  @GetMapping("/new")
  public String newModal(Model model) {
    model.addAttribute("form", new ItemForm());
    return "items/fragments/modal :: modal";
  }

  @PostMapping
  public String create(
      @Valid @ModelAttribute("form") ItemForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=item.create errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      return "items/fragments/modal :: modal";
    }
    String username = principal.getName();
    ItemDTO created = itemService.create(username, form);
    model.addAttribute("item", created);
    HtmxResponse.success(response, getMsg("toast.item.created"));
    return "items/fragments/modal :: created";
  }

  /* ── HTMX 인라인 수정 ── */

  @GetMapping("/{externalId}/edit")
  public String editRow(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("item", itemService.findByExternalId(externalId, username));
    return "items/fragments/card :: edit";
  }

  @PutMapping("/{externalId}")
  public String updateRow(
      @PathVariable UUID externalId,
      @Valid @ModelAttribute("form") ItemForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=item.update itemExternalId={} errorCount={} fields={}",
          externalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("item", itemService.findByExternalId(externalId, username));
      return "items/fragments/card :: edit";
    }
    ItemDTO updated = itemService.update(externalId, form, username);
    model.addAttribute("item", updated);
    HtmxResponse.success(response, getMsg("toast.item.updated"));
    return "items/fragments/card :: view";
  }

  @GetMapping("/{externalId}/cancel")
  public String cancelEdit(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("item", itemService.findByExternalId(externalId, username));
    return "items/fragments/card :: view";
  }

  @GetMapping("/{externalId}/spaces")
  public String spaces(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("externalId", externalId);
    model.addAttribute("itemName", itemService.findByExternalId(externalId, username).getName());
    model.addAttribute("spaceStocks", itemService.findSpaceStock(externalId, username));
    return "items/fragments/space-stock-modal :: modal";
  }

  @GetMapping("/{externalId}/add-stock")
  public String addStockModal(
      @PathVariable UUID externalId,
      @RequestParam(required = false) UUID spaceId,
      @RequestParam(required = false) UUID shelfId,
      @RequestParam(required = false, defaultValue = "false") boolean direct,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var item = itemService.findByExternalId(externalId, username);
    model.addAttribute("item", item);

    if (spaceId == null) {
      List<SpaceDTO> spaces = spaceService.findAllByUsername(username);
      model.addAttribute("spaces", spaces);
      StockForm form = new StockForm();
      form.setItemExternalId(externalId);
      model.addAttribute("form", form);
      return "items/fragments/add-stock-modal :: unified-form";
    }

    model.addAttribute("spaceId", spaceId);
    model.addAttribute("spaceName", spaceService.findByExternalId(spaceId, username).getName());

    if (shelfId == null && !direct) {
      model.addAttribute("shelves", shelfService.findAllBySpaceId(spaceId, username));
      return "items/fragments/add-stock-modal :: shelf-oob";
    }

    // shelf selected or direct (공간 루트)
    if (shelfId != null) {
      model.addAttribute("shelfId", shelfId);
      model.addAttribute(
          "shelfName", shelfService.findByExternalId(spaceId, shelfId, username).getName());
      model.addAttribute("boxes", boxService.findAllByShelfId(spaceId, shelfId, username));
    }
    return "items/fragments/add-stock-modal :: box-oob";
  }

  @PostMapping("/{externalId}/stocks")
  public String addStock(
      @PathVariable UUID externalId,
      @Valid @ModelAttribute("form") StockForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response) {
    String username = principal.getName();
    form.setItemExternalId(externalId);

    validateSingleSelect(
        request, "shelfExternalId", result, "valid.stock.shelfExternalId.multiple");
    validateSingleSelect(request, "boxExternalId", result, "valid.stock.boxExternalId.multiple");

    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=item.addStock itemExternalId={} errorCount={}",
          externalId,
          result.getErrorCount());
      model.addAttribute("item", itemService.findByExternalId(externalId, username));
      model.addAttribute("spaces", spaceService.findAllByUsername(username));
      if (form.getSpaceExternalId() != null) {
        model.addAttribute("spaceId", form.getSpaceExternalId());
        model.addAttribute(
            "shelves", shelfService.findAllBySpaceId(form.getSpaceExternalId(), username));
        if (form.getShelfExternalId() != null) {
          model.addAttribute("shelfId", form.getShelfExternalId());
          model.addAttribute(
              "boxes",
              boxService.findAllByShelfId(
                  form.getSpaceExternalId(), form.getShelfExternalId(), username));
        }
      }
      return "items/fragments/add-stock-modal :: unified-form";
    }

    stockService.create(form, username);
    var item = itemService.findByExternalId(externalId, username);
    model.addAttribute("item", item);
    HtmxResponse.success(response, getMsg("toast.stock.created"));
    return "items/fragments/add-stock-modal :: created";
  }

  private void validateSingleSelect(
      HttpServletRequest request, String param, BindingResult result, String errorCode) {
    String[] values = request.getParameterValues(param);
    if (values != null && values.length > 1) {
      result.rejectValue(param, errorCode);
    }
  }

  @GetMapping("/{externalId}/history")
  public String history(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("itemName", itemService.findByExternalId(externalId, username).getName());
    model.addAttribute("history", itemService.findTransactionHistory(externalId, username));
    return "items/fragments/history-modal :: modal";
  }

  @DeleteMapping("/{externalId}")
  public String delete(
      @PathVariable UUID externalId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "name") String searchType,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    itemService.delete(externalId, username);
    var itemsPage = itemService.findPageByUsername(username, keyword, searchType, sortBy, page);
    model.addAttribute("items", itemsPage.content());
    model.addAttribute("page", itemsPage);
    model.addAttribute("keyword", keyword);
    model.addAttribute("searchType", searchType);
    model.addAttribute("sortBy", sortBy);
    HtmxResponse.success(response, getMsg("toast.item.deleted"));
    return "items/list :: item-list-section";
  }
}
