package com.seu.seustock.model.pagination;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, int totalCount) {

  public int totalPages() {
    return totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
  }

  public boolean hasPrevious() {
    return page > 1;
  }

  public boolean hasNext() {
    return page < totalPages();
  }

  public int nextPage() {
    return page + 1;
  }

  public int previousPage() {
    return page - 1;
  }
}
