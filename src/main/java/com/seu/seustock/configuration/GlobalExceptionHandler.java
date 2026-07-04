package com.seu.seustock.configuration;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request, Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute(
        "errorMessage",
        messageSource.getMessage("error.400.title", null, locale) + ": " + ex.getName());
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 400);
      model.addAttribute("errorTitle", messageSource.getMessage("error.400.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/400";
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleGeneral(Exception ex, HttpServletRequest request, Model model) {
    log.error("unhandled exception", ex);
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute("errorMessage", messageSource.getMessage("error.500.message", null, locale));
    if (isHtmxRequest(request.getHeader("HX-Request"))) {
      model.addAttribute("statusCode", 500);
      model.addAttribute("errorTitle", messageSource.getMessage("error.500.title", null, locale));
      return "fragments/error-modal :: modal";
    }
    return "error/500";
  }

  private boolean isHtmxRequest(String hxRequest) {
    return "true".equalsIgnoreCase(hxRequest);
  }
}
