package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShelfForm {

  @NotBlank(message = "{valid.shelf.name.notBlank}")
  @Size(max = 100, message = "{valid.shelf.name.size}")
  private String name;
}
