package com.seu.seustock.service;

import com.seu.seustock.mapper.ItemLotMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ItemLotDTO;
import com.seu.seustock.model.dto.ItemLotUnitDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class ItemLotService extends BaseService {

  private final ItemLotMapper itemLotMapper;

  public ItemLotService(
      ItemLotMapper itemLotMapper, UserMapper userMapper, MessageSource messageSource) {
    super(userMapper, messageSource);
    this.itemLotMapper = itemLotMapper;
  }

  public LotDetail findDetail(UUID externalId, String username) {
    UserDTO user = getUser(username);
    ItemLotDTO lot =
        itemLotMapper
            .findByExternalIdAndUserId(externalId, user.getId())
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.lot.notFound")));
    List<ItemLotUnitDTO> units = itemLotMapper.findUnitsByLotExternalId(externalId, user.getId());
    return new LotDetail(lot, units);
  }

  public record LotDetail(ItemLotDTO lot, List<ItemLotUnitDTO> units) {}
}
