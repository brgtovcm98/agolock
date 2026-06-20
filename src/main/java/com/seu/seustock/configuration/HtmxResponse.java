package com.seu.seustock.configuration;

import jakarta.servlet.http.HttpServletResponse;

public final class HtmxResponse {

  private HtmxResponse() {}

  public static void success(HttpServletResponse response, String message) {
    toast(response, "success", message);
  }

  public static void error(HttpServletResponse response, String message) {
    toast(response, "error", message);
  }

  private static void toast(HttpServletResponse response, String type, String message) {
    response.addHeader(
        "HX-Trigger",
        "{\"app:toast\":{\"type\":\""
            + asciiJsonString(type)
            + "\",\"message\":\""
            + asciiJsonString(message)
            + "\"}}");
  }

  private static String asciiJsonString(String value) {
    if (value == null) {
      return "";
    }

    StringBuilder builder = new StringBuilder(value.length());
    value.codePoints().forEach(codePoint -> appendEscapedJson(builder, codePoint));
    return builder.toString();
  }

  private static void appendEscapedJson(StringBuilder builder, int codePoint) {
    switch (codePoint) {
      case '\\' -> builder.append("\\\\");
      case '"' -> builder.append("\\\"");
      case '\b' -> builder.append("\\b");
      case '\f' -> builder.append("\\f");
      case '\n' -> builder.append("\\n");
      case '\r' -> builder.append("\\r");
      case '\t' -> builder.append("\\t");
      default -> {
        if (codePoint < 0x20 || codePoint > 0x7E) {
          appendUnicodeEscape(builder, codePoint);
        } else {
          builder.appendCodePoint(codePoint);
        }
      }
    }
  }

  private static void appendUnicodeEscape(StringBuilder builder, int codePoint) {
    if (Character.isBmpCodePoint(codePoint)) {
      builder.append("\\u").append(String.format("%04x", codePoint));
      return;
    }

    char[] surrogates = Character.toChars(codePoint);
    for (char surrogate : surrogates) {
      builder.append("\\u").append(String.format("%04x", (int) surrogate));
    }
  }
}
