package com.seu.seustock.controller;

import com.seu.seustock.model.form.AccountNicknameForm;
import com.seu.seustock.model.form.AccountPasswordForm;
import com.seu.seustock.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AccountController {

  private final UserService userService;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @GetMapping("/account")
  public String accountForm(Model model) {
    if (!model.containsAttribute("nicknameForm")) {
      model.addAttribute("nicknameForm", new AccountNicknameForm());
    }
    if (!model.containsAttribute("passwordForm")) {
      model.addAttribute("passwordForm", new AccountPasswordForm());
    }
    return "account";
  }

  @PostMapping("/account/nickname")
  public String updateNickname(
      @Valid @ModelAttribute("nicknameForm") AccountNicknameForm form,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirectAttributes,
      Model model) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=account.updateNickname errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("passwordForm", new AccountPasswordForm());
      return "account";
    }
    userService.updateNickname(principal.getName(), form.getNickname());
    redirectAttributes.addFlashAttribute("toastMessage", getMsg("toast.account.nicknameUpdated"));
    redirectAttributes.addFlashAttribute("toastType", "success");
    return "redirect:/account";
  }

  @PostMapping("/account/password")
  public String updatePassword(
      @Valid @ModelAttribute("passwordForm") AccountPasswordForm form,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirectAttributes,
      Model model) {
    if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
      result.rejectValue("newPasswordConfirm", "match", getMsg("error.password.mismatch"));
    }
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=account.updatePassword errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("nicknameForm", new AccountNicknameForm());
      return "account";
    }
    try {
      userService.updatePassword(
          principal.getName(), form.getCurrentPassword(), form.getNewPassword());
    } catch (IllegalArgumentException e) {
      result.rejectValue("currentPassword", "incorrect", getMsg(e.getMessage()));
      model.addAttribute("nicknameForm", new AccountNicknameForm());
      return "account";
    }
    redirectAttributes.addFlashAttribute("toastMessage", getMsg("toast.account.passwordUpdated"));
    redirectAttributes.addFlashAttribute("toastType", "success");
    return "redirect:/account";
  }
}
