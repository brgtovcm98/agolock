package com.seu.seustock.controller;

import java.util.List;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

final class ControllerLogSupport {

  private ControllerLogSupport() {}

  static List<String> invalidFields(BindingResult result) {
    return result.getFieldErrors().stream().map(FieldError::getField).distinct().toList();
  }
}
