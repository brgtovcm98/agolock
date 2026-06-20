package com.seu.seustock.controller;

import com.seu.seustock.configuration.HtmxResponse;
import com.seu.seustock.model.form.BoxForm;
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
public class BoxController {

  private final BoxService boxService;
  private final ShelfService shelfService;
  private final org.springframework.context.MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(
        key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale());
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/{boxExternalId}/edit")
  public String editModal(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @PathVariable UUID boxExternalId,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var box =
        boxService.findByExternalId(spaceExternalId, shelfExternalId, boxExternalId, username);
    BoxForm form = new BoxForm();
    form.setName(box.getName());
    model.addAttribute("spaceExternalId", spaceExternalId);
    model.addAttribute("shelfExternalId", shelfExternalId);
    model.addAttribute("boxExternalId", boxExternalId);
    model.addAttribute("form", form);
    return "boxes/fragments/modal :: edit-modal";
  }

  @PatchMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/{boxExternalId}")
  public String rename(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @PathVariable UUID boxExternalId,
      @Valid @ModelAttribute("form") BoxForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=box.rename spaceExternalId={} shelfExternalId={} boxExternalId={} errorCount={} fields={}",
          spaceExternalId,
          shelfExternalId,
          boxExternalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("spaceExternalId", spaceExternalId);
      model.addAttribute("shelfExternalId", shelfExternalId);
      model.addAttribute("boxExternalId", boxExternalId);
      return "boxes/fragments/modal :: edit-modal";
    }
    boxService.rename(spaceExternalId, shelfExternalId, boxExternalId, form, username);
    model.addAttribute("spaceExternalId", spaceExternalId);
    model.addAttribute(
        "shelf", shelfService.findByExternalId(spaceExternalId, shelfExternalId, username));
    model.addAttribute(
        "boxes", boxService.findAllByShelfId(spaceExternalId, shelfExternalId, username));
    HtmxResponse.success(response, "박스가 변경되었습니다.");
    return "shelves/fragments/box-list :: box-list-response";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/new")
  public String newModal(
      @PathVariable UUID spaceExternalId, @PathVariable UUID shelfExternalId, Model model) {
    model.addAttribute("spaceExternalId", spaceExternalId);
    model.addAttribute("shelfExternalId", shelfExternalId);
    model.addAttribute("form", new BoxForm());
    return "boxes/fragments/modal :: modal";
  }

  @PostMapping("/shelves/{shelfExternalId}/boxes")
  public String create(
      @PathVariable UUID shelfExternalId,
      @RequestParam UUID spaceExternalId,
      @Valid @ModelAttribute("form") BoxForm form,
      BindingResult result,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=box.create spaceExternalId={} shelfExternalId={} errorCount={} fields={}",
          spaceExternalId,
          shelfExternalId,
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("spaceExternalId", spaceExternalId);
      model.addAttribute("shelfExternalId", shelfExternalId);
      return "boxes/fragments/modal :: modal";
    }
    boxService.create(spaceExternalId, shelfExternalId, form, username);
    model.addAttribute("spaceExternalId", spaceExternalId);
    model.addAttribute("shelfExternalId", shelfExternalId);
    model.addAttribute(
        "shelf", shelfService.findByExternalId(spaceExternalId, shelfExternalId, username));
    model.addAttribute(
        "boxes", boxService.findAllByShelfId(spaceExternalId, shelfExternalId, username));
    HtmxResponse.success(response, "박스가 추가되었습니다.");
    return "shelves/fragments/box-list :: box-list-response";
  }

  @DeleteMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/{boxExternalId}")
  public String delete(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @PathVariable UUID boxExternalId,
      Principal principal,
      Model model,
      HttpServletResponse response) {
    String username = principal.getName();
    boxService.delete(spaceExternalId, shelfExternalId, boxExternalId, username);
    model.addAttribute("spaceExternalId", spaceExternalId);
    model.addAttribute(
        "shelf", shelfService.findByExternalId(spaceExternalId, shelfExternalId, username));
    model.addAttribute(
        "boxes", boxService.findAllByShelfId(spaceExternalId, shelfExternalId, username));
    HtmxResponse.success(response, "박스가 삭제되었습니다.");
    return "shelves/fragments/box-list :: box-list-container";
  }
}
