package com.seu.seustock.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컨트롤러 웹 테스트 공통 기반 클래스.
 *
 * <p>모든 컨트롤러 테스트 클래스가 상속받아 사용한다. 서비스 계층은 각 하위 클래스에서 {@code @MockitoBean}으로 대체하여 HTTP
 * 계약(라우팅·보안·Validation·HTMX 응답 형태)만 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class AbstractControllerTest {

  @Autowired protected MockMvc mockMvc;

  // 경로 변수로 사용할 고정 UUID (서비스가 Mock이므로 실제 DB 레코드 불필요)
  protected static final UUID SPACE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  protected static final UUID SHELF_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
  protected static final UUID BOX_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
  protected static final UUID ITEM_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
  protected static final UUID STOCK_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
  protected static final UUID IMAGE_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

  /**
   * 응답에 {@code HX-Trigger} 헤더가 존재하고 {@code "app:toast"}를 포함하는지 검증하는 ResultMatcher. 성공/오류 HTMX 토스트
   * 알림 검증에 사용.
   */
  protected static ResultMatcher hasToastTrigger() {
    return header().string("HX-Trigger", containsString("app:toast"));
  }
}
