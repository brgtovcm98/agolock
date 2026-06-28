package com.seu.seustock.configuration;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleNotFound(NoSuchElementException ex, HttpServletRequest request, Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute("errorMessage", ex.getMessage());
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 404);
      model.addAttribute("errorTitle", messageSource.getMessage("error.404.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/404";
  }

  @ExceptionHandler(SecurityException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public String handleForbidden(SecurityException ex, HttpServletRequest request, Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute("errorMessage", ex.getMessage());
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 403);
      model.addAttribute("errorTitle", messageSource.getMessage("error.403.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/403";
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleBadRequest(RuntimeException ex, HttpServletRequest request, Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute("errorMessage", ex.getMessage());
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 400);
      model.addAttribute("errorTitle", messageSource.getMessage("error.400.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/400";
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex, HttpServletRequest request, Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    String errorMessage =
        messageSource.getMessage(
            "error.image.sizeExceeded", null, "업로드 파일 크기 제한(10MB)을 초과했습니다.", locale);
    model.addAttribute("errorMessage", errorMessage);
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 400);
      model.addAttribute("errorTitle", messageSource.getMessage("error.400.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/400";
  }

  private boolean isHtmxRequest(String hxRequest) {
    return "true".equalsIgnoreCase(hxRequest);
  }
}
