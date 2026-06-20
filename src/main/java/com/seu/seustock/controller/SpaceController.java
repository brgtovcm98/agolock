package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.form.SpaceForm;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/spaces")
@RequiredArgsConstructor
@Slf4j
public class SpaceController {

  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final StockService stockService;
  private final org.springframework.context.MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(
        key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale());
  }

  @GetMapping
  public String list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var spacesPage = spaceService.findPageByUsername(username, keyword, sortBy, page);
    model.addAttribute("spaces", spacesPage.content());
    model.addAttribute("page", spacesPage);
    model.addAttribute("form", new SpaceForm());
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    addSummaries(model, spacesPage.content());
    if (append) {
      return "spaces/fragments/list-response :: space-more-response";
    }
    return "spaces/list";
  }

  @GetMapping("/{externalId}")
  public String detail(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(externalId, username);
    model.addAttribute("space", space);
    model.addAttribute("shelves", shelfService.findAllBySpaceId(externalId, username));
    model.addAttribute("stocks", stockService.findPanelBySpace(externalId, username));
    model.addAttribute("breadcrumb", space.getName());
    model.addAttribute("spaceExternalId", externalId);
    return "spaces/detail";
  }

  @GetMapping("/new")
  public String newModal(Model model) {
    model.addAttribute("form", new SpaceForm());
    return "spaces/fragments/modal :: modal";
  }

  @PostMapping
  public String create(
      @Valid @ModelAttribute("form") SpaceForm form,
      BindingResult result,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
      Principal principal,
      Model model,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=space.create errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      if ("true".equals(htmxRequest)) {
        return "spaces/fragments/modal :: modal";
      }
      var spacesPage = spaceService.findPageByUsername(username, keyword, sortBy, page);
      model.addAttribute("spaces", spacesPage.content());
      model.addAttribute("page", spacesPage);
      model.addAttribute("keyword", keyword);
      model.addAttribute("sortBy", sortBy);
      addSummaries(model, spacesPage.content());
      return "spaces/list";
    }
    SpaceDTO created = spaceService.create(username, form);
    if ("true".equals(htmxRequest)) {
      model.addAttribute("space", created);
      addSummaries(model, List.of(created));
      HtmxResponse.success(response, getMsg("toast.space.created"));
      return "spaces/fragments/modal :: created";
    }
    redirectAttributes.addFlashAttribute("toastType", "success");
    redirectAttributes.addFlashAttribute("toastMessage", getMsg("toast.space.created"));
    return "redirect:/spaces";
  }

  /* ── HTMX 인라인 수정 ── */

  @GetMapping("/{externalId}/edit")
  public String editRow(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("space", spaceService.findByExternalId(externalId, username));
    return "spaces/fragments/row :: edit";
  }

  @PutMapping("/{externalId}")
  public String updateRow(
      @PathVariable UUID externalId,
      @Valid SpaceForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=space.update spaceExternalId={} errorCount={} fields={}",
          externalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("space", spaceService.findByExternalId(externalId, username));
      return "spaces/fragments/row :: edit";
    }
    SpaceDTO updated = spaceService.update(externalId, form, username);
    model.addAttribute("space", updated);
    addSummaries(model, List.of(updated));
    HtmxResponse.success(response, getMsg("toast.space.updated"));
    return "spaces/fragments/row :: view";
  }

  @GetMapping("/{externalId}/cancel")
  public String cancelEdit(@PathVariable UUID externalId, Principal principal, Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(externalId, username);
    model.addAttribute("space", space);
    addSummaries(model, List.of(space));
    return "spaces/fragments/row :: view";
  }

  @DeleteMapping("/{externalId}")
  public String delete(
      @PathVariable UUID externalId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    spaceService.delete(externalId, username);
    var spacesPage = spaceService.findPageByUsername(username, keyword, sortBy, page);
    model.addAttribute("spaces", spacesPage.content());
    model.addAttribute("page", spacesPage);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    addSummaries(model, spacesPage.content());
    HtmxResponse.success(response, getMsg("toast.space.deleted"));
    return "spaces/list :: space-list-section";
  }

  /**
   * Attach the per-space summary map for any path that renders {@code spaces/fragments/row ::
   * view}.
   */
  private void addSummaries(Model model, List<SpaceDTO> spaces) {
    model.addAttribute("summaries", spaceService.findSummariesByExternalId(spaces));
  }
}
