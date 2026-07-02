package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class HtmxResponseTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final TypeReference<Map<String, Map<String, String>>> TRIGGER_TYPE =
      new TypeReference<>() {};

  @Test
  void success_setsHxTriggerHeaderWithAsciiSafeJson() throws JsonProcessingException {
    MockHttpServletResponse response = new MockHttpServletResponse();

    HtmxResponse.success(response, "품목과 재고가 추가되었습니다.");

    String header = response.getHeader("HX-Trigger");
    Map<String, Map<String, String>> trigger = objectMapper.readValue(header, TRIGGER_TYPE);

    assertThat(header).contains("\"type\":\"success\"");
    assertThat(header).contains("app:toast");
    assertThat(header).doesNotContain("품목과 재고가 추가되었습니다.");
    assertThat(header).contains("\\u");
    assertThat(header.chars()).allMatch(c -> c <= 0x7f);
    assertThat(trigger.get("app:toast"))
        .containsEntry("type", "success")
        .containsEntry("message", "품목과 재고가 추가되었습니다.");
  }

  @Test
  void error_setsHxTriggerHeaderWithAsciiSafeJson() throws JsonProcessingException {
    MockHttpServletResponse response = new MockHttpServletResponse();

    HtmxResponse.error(response, "오류가 발생했습니다.");

    String header = response.getHeader("HX-Trigger");
    Map<String, Map<String, String>> trigger = objectMapper.readValue(header, TRIGGER_TYPE);

    assertThat(header).contains("\"type\":\"error\"");
    assertThat(header).contains("app:toast");
    assertThat(header).doesNotContain("오류가 발생했습니다.");
    assertThat(header).contains("\\u");
    assertThat(header.chars()).allMatch(c -> c <= 0x7f);
    assertThat(trigger.get("app:toast"))
        .containsEntry("type", "error")
        .containsEntry("message", "오류가 발생했습니다.");
  }
}
