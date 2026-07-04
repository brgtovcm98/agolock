package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class SpaceForm {

  @NotBlank(message = "{valid.space.name.notBlank}")
  @Size(max = 100, message = "{valid.space.name.size}")
  private String name;

  private MultipartFile imageFile;
  private String imageHash;
}
