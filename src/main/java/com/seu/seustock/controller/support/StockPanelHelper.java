package com.seu.seustock.controller.support;

import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.form.StockMoveForm;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 재고 패널 렌더링과 이동 대상 위치 목록 조회를 담당하는 공통 헬퍼. */
@Component
@RequiredArgsConstructor
public class StockPanelHelper {

  private final StockService stockService;
  private final SpaceService spaceService;
  private final ShelfService shelfService;
  private final BoxService boxService;
  private final MessageSource messageSource;

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /**
   * 주어진 위치(공간/선반/박스)의 재고 패널 조회 결과를 모델에 담고 {@code stocks/fragments/panel :: stock-panel-response}
   * 프래그먼트 이름을 반환한다.
   */
  public String buildPanelResponse(
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      boolean isAllView,
      String keyword,
      String sortBy,
      String username,
      Model model) {
    String breadcrumb;
    var page =
        boxExternalId != null
            ? stockService.findPanelPageByBox(
                spaceExternalId, shelfExternalId, boxExternalId, username, 1)
            : shelfExternalId != null
                ? stockService.findPanelPageByShelf(spaceExternalId, shelfExternalId, username, 1)
                : isAllView
                    ? stockService.findPanelPageBySpaceAll(
                        spaceExternalId, keyword, sortBy, username, 1)
                    : stockService.findPanelPageBySpace(spaceExternalId, username, 1);
    if (boxExternalId != null) {
      breadcrumb =
          boxService
              .findByExternalId(spaceExternalId, shelfExternalId, boxExternalId, username)
              .getName();
    } else if (shelfExternalId != null) {
      breadcrumb =
          shelfService.findByExternalId(spaceExternalId, shelfExternalId, username).getName();
    } else {
      SpaceDTO space = spaceService.findByExternalId(spaceExternalId, username);
      breadcrumb =
          isAllView
              ? getMsg("view.stock.breadcrumb.all", space.getName())
              : getMsg("view.stock.breadcrumb.loose", space.getName());
    }
    model.addAttribute("stocks", page.content());
    model.addAttribute("page", page);
    model.addAttribute("breadcrumb", breadcrumb);
    model.addAttribute("space", spaceExternalId);
    model.addAttribute("shelf", shelfExternalId);
    model.addAttribute("box", boxExternalId);
    model.addAttribute("allView", isAllView);
    model.addAttribute("keyword", keyword);
    model.addAttribute("sortBy", sortBy);
    return "stocks/fragments/panel :: stock-panel-response";
  }

  /** 이동 폼의 출발지를 기준으로 선택 가능한 위치 옵션 목록을 구성한다. */
  public List<MoveLocationOption> buildMoveLocationOptions(StockMoveForm form, String username) {
    List<MoveLocationOption> options = new ArrayList<>();
    UUID spaceExternalId = form.getSourceSpaceExternalId();
    String spaceName = spaceService.findByExternalId(spaceExternalId, username).getName();
    options.add(
        new MoveLocationOption(
            getMsg("view.stock.breadcrumb.loose", spaceName),
            spaceExternalId,
            null,
            null,
            isSameLocation(form, spaceExternalId, null, null)));

    for (var shelf : shelfService.findAllBySpaceId(spaceExternalId, username)) {
      options.add(
          new MoveLocationOption(
              shelf.getName(),
              spaceExternalId,
              shelf.getExternalId(),
              null,
              isSameLocation(form, spaceExternalId, shelf.getExternalId(), null)));

      for (var box :
          boxService.findAllByShelfId(spaceExternalId, shelf.getExternalId(), username)) {
        options.add(
            new MoveLocationOption(
                shelf.getName() + " / " + box.getName(),
                spaceExternalId,
                shelf.getExternalId(),
                box.getExternalId(),
                isSameLocation(form, spaceExternalId, shelf.getExternalId(), box.getExternalId())));
      }
    }
    return options;
  }

  private boolean isSameLocation(
      StockMoveForm form, UUID spaceExternalId, UUID shelfExternalId, UUID boxExternalId) {
    return Objects.equals(form.getSourceSpaceExternalId(), spaceExternalId)
        && Objects.equals(form.getSourceShelfExternalId(), shelfExternalId)
        && Objects.equals(form.getSourceBoxExternalId(), boxExternalId);
  }

  public record MoveLocationOption(
      String label,
      UUID spaceExternalId,
      UUID shelfExternalId,
      UUID boxExternalId,
      boolean current) {}
}
