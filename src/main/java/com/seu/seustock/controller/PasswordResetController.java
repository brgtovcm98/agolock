package com.seu.seustock.controller;

import com.seu.seustock.model.form.ForgotPasswordForm;
import com.seu.seustock.model.form.ResetPasswordForm;
import com.seu.seustock.service.PasswordResetService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

  private final PasswordResetService passwordResetService;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  // ── 비밀번호 찾기 (재설정 메일 요청) ──────────────────────────────────────

  @GetMapping("/password/forgot")
  public String forgotForm(
      @ModelAttribute("form") ForgotPasswordForm form,
      @RequestParam(required = false) String sent,
      Model model) {
    model.addAttribute("sent", sent != null);
    return "forgot-password";
  }

  @PostMapping("/password/forgot")
  public String forgot(
      @Valid @ModelAttribute("form") ForgotPasswordForm form,
      BindingResult result,
      RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=password.forgot errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      return "forgot-password";
    }
    try {
      passwordResetService.requestReset(form.getEmail());
    } catch (RuntimeException e) {
      log.error("password reset request failed email={}", form.getEmail(), e);
      redirectAttributes.addFlashAttribute(
          "errorMessage", getMsg("error.passwordReset.sendFailed"));
      return "redirect:/password/forgot";
    }
    // 재발송 버튼이 이메일을 다시 보낼 수 있도록 flash로 전달(URL에 노출하지 않음).
    redirectAttributes.addFlashAttribute("email", form.getEmail());
    return "redirect:/password/forgot?sent";
  }

  // ── 비밀번호 변경 (재설정 링크로 진입) ────────────────────────────────────

  @GetMapping("/password/reset")
  public String resetForm(@RequestParam(required = false) String token, Model model) {
    boolean valid = token != null && passwordResetService.validateToken(token);
    if (!valid) {
      log.warn("password reset form rejected reason=invalid_token tokenPresent={}", token != null);
    }
    model.addAttribute("valid", valid);
    if (valid) {
      ResetPasswordForm form = new ResetPasswordForm();
      form.setToken(token);
      model.addAttribute("form", form);
    }
    return "reset-password";
  }

  @PostMapping("/password/reset")
  public String reset(
      @Valid @ModelAttribute("form") ResetPasswordForm form, BindingResult result, Model model) {
    if (!form.getPassword().equals(form.getPasswordConfirm())) {
      log.warn("password reset rejected reason=password_mismatch");
      result.rejectValue("passwordConfirm", "match", getMsg("error.password.mismatch"));
    }
    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=password.reset errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      model.addAttribute("valid", true);
      return "reset-password";
    }
    passwordResetService.resetPassword(form.getToken(), form.getPassword());
    return "redirect:/login?reset";
  }
}
