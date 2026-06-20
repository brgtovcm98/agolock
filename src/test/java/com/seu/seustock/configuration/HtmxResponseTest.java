package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class HtmxResponseTest {

  @Test
  void success_escapesNonAsciiMessageForHttpHeader() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    HtmxResponse.success(response, "품목과 재고가 추가되었습니다.");

    String header = response.getHeader("HX-Trigger");
    assertThat(header).contains("\"type\":\"success\"");
    assertThat(header).contains("\\ud488\\ubaa9\\uacfc");
    assertThat(header.chars().allMatch(character -> character < 128)).isTrue();
  }
}
