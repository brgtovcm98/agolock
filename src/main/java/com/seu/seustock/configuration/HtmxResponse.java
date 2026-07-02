package com.seu.seustock.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HtmxResponse {

  private static final Logger log = LoggerFactory.getLogger(HtmxResponse.class);
  private static final ObjectMapper objectMapper =
      JsonMapper.builder().configure(JsonWriteFeature.ESCAPE_NON_ASCII, true).build();

  private HtmxResponse() {}

  public static void success(HttpServletResponse response, String message) {
    toast(response, "success", message);
  }

  public static void error(HttpServletResponse response, String message) {
    toast(response, "error", message);
  }

  private static void toast(HttpServletResponse response, String type, String message) {
    Map<String, Map<String, String>> trigger =
        Map.of("app:toast", Map.of("type", type, "message", message));
    try {
      response.addHeader("HX-Trigger", objectMapper.writeValueAsString(trigger));
    } catch (JsonProcessingException e) {
      log.error("failed to serialize HX-Trigger header", e);
    }
  }
}
