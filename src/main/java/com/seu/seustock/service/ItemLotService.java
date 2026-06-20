package com.seu.seustock.service;

import com.seu.seustock.mapper.ItemLotMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.ItemLotUnitDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ItemLotService {

  private final ItemLotMapper itemLotMapper;
  private final UserMapper userMapper;
  private final MessageSource messageSource;

  public LotDetail findDetail(UUID externalId, String username) {
    UserDTO user =
        userMapper
            .findByEmail(username)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
    ItemLotDTO lot =
        itemLotMapper
            .findByExternalIdAndUserId(externalId, user.getId())
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.lot.notFound")));
    List<ItemLotUnitDTO> units = itemLotMapper.findUnitsByLotExternalId(externalId, user.getId());
    return new LotDetail(lot, units);
  }

  private String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  public record LotDetail(ItemLotDTO lot, List<ItemLotUnitDTO> units) {}
}
