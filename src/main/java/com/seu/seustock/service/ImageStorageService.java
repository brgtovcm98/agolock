package com.seu.seustock.service;

import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
  ImageDTO loadForUser(UUID externalId, String username);

  ImageDTO store(MultipartFile file, UserDTO owner, String contentHash);

  Resource load(ImageDTO image);
}
