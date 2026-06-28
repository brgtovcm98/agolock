package com.seu.seustock.model.pagination;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 페이징 처리 보일러플레이트 제거 유틸리티.
 *
 * <p>사용 예:
 *
 * <pre>{@code
 * PageResult<ItemDTO> result = PaginationHelper.execute(
 *     page,
 *     () -> itemMapper.countByUserId(user.getId()),
 *     (size, offset) -> itemMapper.findByUserIdPaged(user.getId(), size, offset)
 * );
 * }</pre>
 */
public final class PaginationHelper {

  private PaginationHelper() {}

  /**
   * 페이징 조회를 수행합니다.
   *
   * @param page 요청 페이지 번호 (1-based)
   * @param countSupplier 전체 건수를 반환하는 Supplier
   * @param fetchFunction (limit, offset) → 데이터 리스트를 반환하는 함수
   * @param <T> 조회 결과 타입
   * @return 페이징된 결과
   */
  public static <T> PageResult<T> execute(
      Integer page,
      Supplier<Integer> countSupplier,
      BiFunction<Integer, Integer, List<T>> fetchFunction) {
    int totalCount = countSupplier.get();
    PageRequest pageRequest = PageRequest.of(page, totalCount);
    List<T> items = fetchFunction.apply(pageRequest.size(), pageRequest.offset());
    return new PageResult<>(items, pageRequest.page(), pageRequest.size(), totalCount);
  }
}
