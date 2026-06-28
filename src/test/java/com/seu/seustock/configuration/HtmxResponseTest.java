package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class HtmxResponseTest {

  @Test
  void success_setsHxTriggerHeaderWithValidJson() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    HtmxResponse.success(response, "품목과 재고가 추가되었습니다.");

    String header = response.getHeader("HX-Trigger");
    assertThat(header).contains("\"type\":\"success\"");
    assertThat(header).contains("app:toast");
    assertThat(header).contains("품목과 재고가 추가되었습니다.");
  }

  @Test
  void error_setsHxTriggerHeaderWithValidJson() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    HtmxResponse.error(response, "오류가 발생했습니다.");

    String header = response.getHeader("HX-Trigger");
    assertThat(header).contains("\"type\":\"error\"");
    assertThat(header).contains("app:toast");
    assertThat(header).contains("오류가 발생했습니다.");
  }
}
