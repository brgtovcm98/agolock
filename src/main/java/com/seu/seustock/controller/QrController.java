package com.seu.seustock.controller;

import com.google.zxing.WriterException;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.QrCodeService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@Slf4j
public class QrController {

  private final QrCodeService qrCodeService;
  private final BoxService boxService;
  private final ShelfService shelfService;
  private final SpaceService spaceService;

  @Value("${app.qr-base-url}")
  private String qrBaseUrl;

  @GetMapping("/api/qr/modal")
  public String qrModal(
      @RequestParam String type,
      @RequestParam UUID externalId,
      @RequestParam String name,
      org.springframework.ui.Model model) {
    String qrUrl = String.format("%s/qr/%ss/%s", qrBaseUrl, type, externalId);
    model.addAttribute("title", name + " QR 코드");
    model.addAttribute("qrUrl", qrUrl);
    return "fragments/qr-modal :: modal";
  }

  @GetMapping(value = "/api/qr/generate", produces = MediaType.IMAGE_PNG_VALUE)
  @ResponseBody
  public byte[] generateQr(@RequestParam String content) throws IOException, WriterException {
    return qrCodeService.generateQrCodeImage(content, 300, 300);
  }

  @GetMapping("/qr/boxes/{externalId}")
  public String scanBox(@PathVariable UUID externalId, Principal principal) {
    String username = principal.getName();
    BoxDTO box = boxService.findByExternalIdOnly(externalId);
    ShelfDTO shelf = shelfService.findById(box.getShelfId());
    SpaceDTO space = spaceService.findById(shelf.getSpaceId());
    Long userId = spaceService.getUserIdByUsername(username);

    if (!space.getUserId().equals(userId)) {
      log.warn(
          "access denied userId={} resource=box resourceExternalId={} shelfId={} spaceId={}",
          userId,
          externalId,
          shelf.getId(),
          space.getId());
      throw new SecurityException("해당 박스에 접근할 권한이 없습니다.");
    }

    return String.format(
        "redirect:/spaces/%s/shelves/%s/boxes/%s/stocks",
        space.getExternalId(), shelf.getExternalId(), box.getExternalId());
  }

  @GetMapping("/qr/shelves/{externalId}")
  public String scanShelf(@PathVariable UUID externalId, Principal principal) {
    String username = principal.getName();
    ShelfDTO shelf = shelfService.findByExternalIdOnly(externalId);
    SpaceDTO space = spaceService.findById(shelf.getSpaceId());
    Long userId = spaceService.getUserIdByUsername(username);

    if (!space.getUserId().equals(userId)) {
      log.warn(
          "access denied userId={} resource=shelf resourceExternalId={} spaceId={}",
          userId,
          externalId,
          space.getId());
      throw new SecurityException("해당 선반에 접근할 권한이 없습니다.");
    }

    return String.format(
        "redirect:/spaces/%s/shelves/%s/stocks", space.getExternalId(), shelf.getExternalId());
  }
}
