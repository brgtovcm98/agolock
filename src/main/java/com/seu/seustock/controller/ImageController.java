package com.seu.seustock.controller;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.service.ImageStorageService;
import com.seu.seustock.service.ai.AiServiceUnavailableException;
import com.seu.seustock.service.ai.ImageAnalysisService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class ImageController {

  private static final Logger log = LoggerFactory.getLogger(ImageController.class);

  private final ImageStorageService imageStorageService;
  private final ImageAnalysisService imageAnalysisService;
  private final MessageSource messageSource;

  @Qualifier("aiAnalysisExecutor")
  private final Executor aiAnalysisExecutor;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @GetMapping("/images/{externalId}")
  public ResponseEntity<Resource> show(@PathVariable UUID externalId, Principal principal) {
    String username = principal.getName();
    ImageDTO image = imageStorageService.loadForUser(externalId, username);
    Resource resource = imageStorageService.load(image);
    MediaType contentType;
    if (image.getContentType() == null) {
      contentType = MediaType.APPLICATION_OCTET_STREAM;
    } else {
      try {
        contentType = MediaType.parseMediaType(image.getContentType());
      } catch (org.springframework.http.InvalidMediaTypeException e) {
        log.warn(
            "invalid media type stored for image externalId={} contentType={}",
            externalId,
            image.getContentType(),
            e);
        contentType = MediaType.APPLICATION_OCTET_STREAM;
      }
    }
    String disposition =
        ContentDisposition.inline()
            .filename(
                image.getOriginalFilename() == null ? "image" : image.getOriginalFilename(),
                StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic().immutable())
        .eTag(externalId.toString())
        .contentType(contentType)
        .header("Content-Disposition", disposition)
        .body(resource);
  }

  @PostMapping("/images/{externalId}/analyze")
  public CompletableFuture<ResponseEntity<Object>> analyzeStored(
      @PathVariable UUID externalId,
      @RequestParam(defaultValue = "0") int retryAttempt,
      @RequestParam(required = false) String previousName,
      @RequestParam(required = false) String previousDescription,
      Principal principal) {
    String username = principal.getName();
    ImageDTO image = imageStorageService.loadForUser(externalId, username);
    Resource resource = imageStorageService.load(image);
    MultipartFile multipartFile = new StoredImageMultipartFile(image, resource);
    log.info(
        "image analysis requested source=stored imageExternalId={} retryAttempt={}",
        externalId,
        retryAttempt);
    return submitAnalysis(
        "analyzeStored",
        () -> {
          ImageAnalysisDTO result =
              imageAnalysisService.analyze(
                  multipartFile, retryAttempt, previousName, previousDescription);
          log.info("image analysis completed source=stored imageExternalId={}", externalId);
          return result;
        });
  }

  @PostMapping("/images/analyze")
  public CompletableFuture<ResponseEntity<Object>> analyze(
      @RequestParam("imageFile") MultipartFile imageFile,
      @RequestParam(defaultValue = "0") int retryAttempt,
      @RequestParam(required = false) String previousName,
      @RequestParam(required = false) String previousDescription) {
    log.info(
        "image analysis requested source=upload contentType={} sizeBytes={} retryAttempt={}",
        imageFile.getContentType(),
        imageFile.getSize(),
        retryAttempt);
    return submitAnalysis(
        "analyze",
        () -> {
          ImageAnalysisDTO result =
              imageAnalysisService.analyze(
                  imageFile, retryAttempt, previousName, previousDescription);
          log.info("image analysis completed source=upload");
          return result;
        });
  }

  private CompletableFuture<ResponseEntity<Object>> submitAnalysis(
      String operation, Supplier<ImageAnalysisDTO> supplier) {
    try {
      return CompletableFuture.supplyAsync(supplier, aiAnalysisExecutor)
          .thenApply(result -> ResponseEntity.ok((Object) result))
          .exceptionally(
              ex -> {
                log.error("image analysis failed operation={}", operation, ex);
                return errorResponse(ex);
              });
    } catch (RejectedExecutionException ex) {
      log.warn("image analysis rejected operation={} reason=executor_saturated", operation, ex);
      return CompletableFuture.completedFuture(
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .body((Object) new ErrorResponse(getMsg("error.ai.rateLimit"))));
    }
  }

  private ResponseEntity<Object> errorResponse(Throwable ex) {
    Throwable cause = unwrap(ex);
    if (cause instanceof AiServiceUnavailableException) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body((Object) new ErrorResponse(cause.getMessage()));
    }
    if (cause instanceof IllegalArgumentException) {
      return ResponseEntity.badRequest().body((Object) new ErrorResponse(cause.getMessage()));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body((Object) new ErrorResponse(getMsg("error.ai.analysisFailed")));
  }

  private Throwable unwrap(Throwable ex) {
    if (ex instanceof CompletionException && ex.getCause() != null) {
      return ex.getCause();
    }
    return ex;
  }

  private record ErrorResponse(String message) {}

  private record StoredImageMultipartFile(ImageDTO image, Resource resource)
      implements MultipartFile {
    @Override
    public String getName() {
      return "imageFile";
    }

    @Override
    public String getOriginalFilename() {
      return image.getOriginalFilename();
    }

    @Override
    public String getContentType() {
      return image.getContentType();
    }

    @Override
    public boolean isEmpty() {
      try {
        return resource.contentLength() == 0;
      } catch (IOException e) {
        return false;
      }
    }

    @Override
    public long getSize() {
      try {
        return resource.contentLength();
      } catch (IOException e) {
        return 0;
      }
    }

    @Override
    public byte[] getBytes() throws IOException {
      return resource.getInputStream().readAllBytes();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return resource.getInputStream();
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
