package com.seu.seustock.controller;

import com.seu.seustock.model.form.LoginForm;
import com.seu.seustock.model.form.UserRegistrationForm;
import com.seu.seustock.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;

  @GetMapping("/register")
  public String registerForm(@RequestParam(required = false) String success, Model model) {
    model.addAttribute("form", new UserRegistrationForm());
    model.addAttribute("success", success != null);
    return "register";
  }

  @PostMapping("/register")
  public String register(
      @Valid @ModelAttribute("form") UserRegistrationForm form, BindingResult result) {
    if (!form.getPassword().equals(form.getPasswordConfirm())) {
      log.warn("registration rejected reason=password_mismatch");
      result.rejectValue("passwordConfirm", "match", "비밀번호가 일치하지 않습니다.");
    }

    if (!result.hasFieldErrors("email") && userService.existsByEmail(form.getEmail())) {
      log.warn("registration rejected reason=email_duplicate");
      result.rejectValue("email", "duplicate", "이미 사용 중인 이메일입니다.");
    }

    if (result.hasErrors()) {
      log.warn(
          "request validation failed operation=user.register errorCount={} fields={}",
          result.getErrorCount(),
          ControllerLogSupport.invalidFields(result));
      return "register";
    }

    userService.register(form);
    return "redirect:/register?success";
  }

  @GetMapping("/register/check-email")
  public String checkEmail(@RequestParam(defaultValue = "") String email, Model model) {
    model.addAttribute("email", email);
    model.addAttribute("taken", !email.isBlank() && userService.existsByEmail(email));
    model.addAttribute("empty", email.isBlank());
    return "fragments/email-check :: result";
  }

  @GetMapping("/login")
  public String loginForm(Principal principal, Model model) {
    if (principal != null) {
      return "redirect:/";
    }
    model.addAttribute("form", new LoginForm());
    return "login";
  }
}
