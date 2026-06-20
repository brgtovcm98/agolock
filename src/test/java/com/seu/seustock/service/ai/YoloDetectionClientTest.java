package com.seu.seustock.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YoloDetectionClientTest {

  @Test
  void disabled_returnsEmptyWithoutCallingYolo() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    YoloDetectionClient client =
        new YoloDetectionClient(builder.baseUrl("http://localhost:8000").build(), false);

    List<YoloDetection> detections = client.detect(new byte[] {1, 2, 3}, "image/jpeg");

    assertThat(detections).isEmpty();
    server.verify();
  }

  @Test
  void enabled_postsToDetectEndpointAndParsesDetections() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://localhost:8000/detect"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
        .andExpect(content().string(containsString("name=\"file\"")))
        .andExpect(content().string(not(containsString("name=\"imageFile\""))))
        .andRespond(
            withSuccess(
                """
                        {
                          "count": 1,
                          "objects": [
                            {
                              "label": "bottle",
                              "confidence": 0.91,
                              "bbox": {
                                "x1": 10.0,
                                "y1": 20.0,
                                "x2": 100.0,
                                "y2": 140.0
                              }
                            }
                          ]
                        }
                        """,
                MediaType.APPLICATION_JSON));

    YoloDetectionClient client =
        new YoloDetectionClient(builder.baseUrl("http://localhost:8000").build(), true);

    List<YoloDetection> detections = client.detect(new byte[] {1, 2, 3}, "image/jpeg");

    assertThat(detections).hasSize(1);
    assertThat(detections.getFirst().label()).isEqualTo("bottle");
    assertThat(detections.getFirst().confidence()).isEqualTo(0.91);
    assertThat(detections.getFirst().x1()).isEqualTo(10.0);
    assertThat(detections.getFirst().y1()).isEqualTo(20.0);
    assertThat(detections.getFirst().x2()).isEqualTo(100.0);
    assertThat(detections.getFirst().y2()).isEqualTo(140.0);
    server.verify();
  }

  @Test
  void enabled_returnsEmptyWhenObjectsMissing() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://localhost:8000/detect"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                        {
                          "count": 0
                        }
                        """,
                MediaType.APPLICATION_JSON));

    YoloDetectionClient client =
        new YoloDetectionClient(builder.baseUrl("http://localhost:8000").build(), true);

    List<YoloDetection> detections = client.detect(new byte[] {1, 2, 3}, "image/jpeg");

    assertThat(detections).isEmpty();
    server.verify();
  }

  @Test
  void enabled_returnsEmptyWhenYoloFails() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://localhost:8000/detect"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    YoloDetectionClient client =
        new YoloDetectionClient(builder.baseUrl("http://localhost:8000").build(), true);

    List<YoloDetection> detections = client.detect(new byte[] {1, 2, 3}, "image/jpeg");

    assertThat(detections).isEmpty();
    server.verify();
  }
}
