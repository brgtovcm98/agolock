package com.seu.seustock.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * ID별 집계 수량을 표현하는 재사용 가능한 DTO.
 *
 * <p>N+1 쿼리를 방지하기 위해 {@code IN} 절로 한 번에 조회한 뒤 서비스에서 맵으로 변환해 사용한다.
 */
@Getter
@Setter
public class IdCountDTO {

  private Long id;
  private int count;
}
