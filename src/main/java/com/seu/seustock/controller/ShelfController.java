package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.form.ShelfForm;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ShelfService;
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
@RequiredArgsConstructor
@Slf4j
public class ShelfController {

  private final ShelfService shelfService;
  private final BoxService boxService;
  private final org.springframework.context.MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(
        key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale());
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes")
  public String boxList(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      Principal principal,
      Model model) {
    String username = principal.getName();
    model.addAttribute("space", spaceExternalId);
    model.addAttribute(
        "shelf", shelfService.findByExternalId(spaceExternalId, shelfExternalId, username));
    model.addAttribute(
        "boxes", boxService.findAllByShelfId(spaceExternalId, shelfExternalId, username));
    return "shelves/fragments/box-list :: box-list";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/edit")
  public String editModal(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var shelf = shelfService.findByExternalId(spaceExternalId, shelfExternalId, username);
    ShelfForm form = new ShelfForm();
    form.setName(shelf.getName());
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("form", form);
    return "shelves/fragments/modal :: edit-modal";
  }

  @PatchMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}")
  public String rename(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @Valid @ModelAttribute("form") ShelfForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=shelf.rename spaceExternalId={} shelfExternalId={} errorCount={} fields={}",
          spaceExternalId,
          shelfExternalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("space", spaceExternalId);
      model.addAttribute("shelf", shelfExternalId);
      return "shelves/fragments/modal :: edit-modal";
    }
    shelfService.rename(spaceExternalId, shelfExternalId, form, username);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelves", shelfService.findAllBySpaceId(spaceExternalId, username));
    HtmxResponse.success(response, getMsg("toast.shelf.updated"));
    return "spaces/fragments/shelf-list-response :: shelf-list-response";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/new")
  public String newModal(@PathVariable UUID spaceExternalId, Model model) {
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("form", new ShelfForm());
    return "shelves/fragments/modal :: modal";
  }

  @PostMapping("/spaces/{spaceExternalId}/shelves")
  public String create(
      @PathVariable UUID spaceExternalId,
      @Valid @ModelAttribute("form") ShelfForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=shelf.create spaceExternalId={} errorCount={} fields={}",
          spaceExternalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("space", spaceExternalId);
      return "shelves/fragments/modal :: modal";
    }
    shelfService.create(spaceExternalId, form, username);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelves", shelfService.findAllBySpaceId(spaceExternalId, username));
    HtmxResponse.success(response, getMsg("toast.shelf.created"));
    return "spaces/fragments/shelf-list-response :: shelf-list-response";
  }

  @DeleteMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}")
  public String delete(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    shelfService.delete(spaceExternalId, shelfExternalId, username);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelves", shelfService.findAllBySpaceId(spaceExternalId, username));
    HtmxResponse.success(response, getMsg("toast.shelf.deleted"));
    return "spaces/detail :: shelf-list";
  }
}
