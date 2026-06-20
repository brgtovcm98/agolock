package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpaceForm {

  @NotBlank(message = "{valid.space.name.notBlank}")
  @Size(max = 100, message = "{valid.space.name.size}")
  private String name;
}
