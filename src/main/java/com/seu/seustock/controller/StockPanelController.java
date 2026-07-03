package com.seu.seustock.controller;

import com.seu.seustock.controller.support.StockPanelHelper;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 공간/선반/박스별 재고 패널 조회를 담당하는 컨트롤러. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StockPanelController {

  private final StockService stockService;
  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final BoxService boxService;
  private final MessageSource messageSource;
  private final StockPanelHelper stockPanelHelper;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @GetMapping("/spaces/{spaceExternalId}/stocks/all")
  public String panelBySpaceAll(
      @PathVariable UUID spaceExternalId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "newest") String sortBy,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
    var stocksPage =
        stockService.findPanelPageBySpaceAll(spaceExternalId, keyword, sortBy, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", getMsg("view.stock.breadcrumb.all", space.getName()));
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("isAllView", true);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/stocks")
  public String panelBySpace(
      @PathVariable UUID spaceExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
    var stocksPage = stockService.findPanelPageBySpace(spaceExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", getMsg("view.stock.breadcrumb.loose", space.getName()));
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/stocks")
  public String panelByShelf(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    ShelfDTO shelf = shelfService.findByExternalId(spaceExternalId, shelfExternalId, username);
    var stocksPage =
        stockService.findPanelPageByShelf(spaceExternalId, shelfExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute("breadcrumb", shelf.getName());
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }

  @GetMapping("/spaces/{spaceExternalId}/shelves/{shelfExternalId}/boxes/{boxExternalId}/stocks")
  public String panelByBox(
      @PathVariable UUID spaceExternalId,
      @PathVariable UUID shelfExternalId,
      @PathVariable UUID boxExternalId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false, defaultValue = "false") boolean append,
      Principal principal,
      Model model) {
    String username = principal.getName();
    var stocksPage =
        stockService.findPanelPageByBox(
            spaceExternalId, shelfExternalId, boxExternalId, username, page);
    model.addAttribute("stocks", stocksPage.content());
    model.addAttribute("page", stocksPage);
    model.addAttribute(
        "breadcrumb",
        boxService
            .findByExternalId(spaceExternalId, shelfExternalId, boxExternalId, username)
            .getName());
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("isAllView", false);
    if (append) {
      return "stocks/fragments/panel :: stock-panel-more-response";
    }
    return "stocks/fragments/panel :: stock-panel";
  }
}
