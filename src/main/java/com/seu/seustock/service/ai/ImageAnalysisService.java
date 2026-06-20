package com.seu.seustock.service.ai;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageAnalysisService {

  ImageAnalysisDTO analyze(MultipartFile imageFile);

  ImageAnalysisDTO analyze(
      MultipartFile imageFile, int retryAttempt, String previousName, String previousDescription);
}
