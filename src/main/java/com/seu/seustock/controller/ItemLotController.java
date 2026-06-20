package com.seu.seustock.controller;

import com.seu.seustock.service.ItemLotService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ItemLotController {

  private final ItemLotService itemLotService;

  @GetMapping("/lots/{externalId}")
  public String detail(@PathVariable UUID externalId, Principal principal, Model model) {
    ItemLotService.LotDetail detail = itemLotService.findDetail(externalId, principal.getName());
    model.addAttribute("lot", detail.lot());
    model.addAttribute("units", detail.units());
    return "lots/fragments/detail-modal :: modal";
  }
}
