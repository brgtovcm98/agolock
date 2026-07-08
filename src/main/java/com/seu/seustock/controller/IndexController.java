package com.seu.seustock.controller;

import com.seu.seustock.model.form.DashboardTargetForm;
import com.seu.seustock.service.DashboardService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IndexController {

  private final DashboardService dashboardService;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @GetMapping("/")
  public String index(Principal principal, Model model) {
    model.addAttribute("summary", dashboardService.getSummary(principal.getName()));
    if (!model.containsAttribute("targetForm")) {
      model.addAttribute("targetForm", new DashboardTargetForm());
    }
    model.addAttribute("activeNav", "home");
    return "dashboard";
  }

  @PostMapping("/dashboard/target")
  public String updateTarget(
      @Valid @ModelAttribute("targetForm") DashboardTargetForm form,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirectAttributes,
      Model model) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=dashboard.updateTarget errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("summary", dashboardService.getSummary(principal.getName()));
      model.addAttribute("activeNav", "home");
      return "dashboard";
    }
    dashboardService.updateTarget(principal.getName(), form.getTarget());
    redirectAttributes.addFlashAttribute("toastMessage", getMsg("toast.dashboard.targetSaved"));
    redirectAttributes.addFlashAttribute("toastType", "success");
    return "redirect:/";
  }

  @GetMapping("/empty")
  @ResponseBody
  public String empty() {
    return "<div id=\"modal\"></div>";
  }

  @GetMapping(
      value = "/.well-known/appspecific/com.chrome.devtools.json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String chromeDevTools() {
    return "{}";
  }
}
