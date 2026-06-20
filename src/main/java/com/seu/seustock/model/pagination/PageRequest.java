package com.seu.seustock.model.pagination;

public record PageRequest(int page, int size) {

  public static final int DEFAULT_SIZE = 10;

  public static PageRequest of(Integer page) {
    return new PageRequest(page == null || page < 1 ? 1 : page, DEFAULT_SIZE);
  }

  public static PageRequest of(Integer page, int totalCount) {
    int normalizedPage = page == null || page < 1 ? 1 : page;
    int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / DEFAULT_SIZE);
    return new PageRequest(Math.min(normalizedPage, totalPages), DEFAULT_SIZE);
  }

  public int offset() {
    return (page - 1) * size;
  }
}
