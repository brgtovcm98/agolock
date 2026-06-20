package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.form.ItemForm;
import com.seu.seustock.service.ItemService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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
  private final org.springframework.context.MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(
        key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale());
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
