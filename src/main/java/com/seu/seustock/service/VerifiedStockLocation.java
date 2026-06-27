package com.seu.seustock.service;

import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;

record VerifiedStockLocation(SpaceDTO space, ShelfDTO shelf, BoxDTO box) {
  Long shelfId() {
    return shelf == null ? null : shelf.getId();
  }

  Long boxId() {
    return box == null ? null : box.getId();
  }
}
