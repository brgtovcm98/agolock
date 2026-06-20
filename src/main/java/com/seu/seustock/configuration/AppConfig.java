package com.seu.seustock.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
public class AppConfig {

  @Value("${security.bcrypt.strength:10}")
  private int bcryptStrength;

  @Value("${seustock.ai.executor.core-pool-size:2}")
  private int aiExecutorCorePoolSize;

  @Value("${seustock.ai.executor.max-pool-size:4}")
  private int aiExecutorMaxPoolSize;

  @Value("${seustock.ai.executor.queue-capacity:10}")
  private int aiExecutorQueueCapacity;

  @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
  private String ollamaBaseUrl;

  @Value("${seustock.ai.ollama.connect-timeout-seconds:10}")
  private int ollamaConnectTimeoutSeconds;

  @Value("${seustock.ai.ollama.read-timeout-seconds:120}")
  private int ollamaReadTimeoutSeconds;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(bcryptStrength);
  }

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  // Java 직렬화 대신 JSON 직렬화로 배포 간 세션 호환성 확보
  // GenericJackson2JsonRedisSerializer 대신 default typing ObjectMapper 사용 (Spring Data Redis 4.x 권장)
  @Bean
  public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.activateDefaultTyping(
        mapper.getPolymorphicTypeValidator(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);
    return new Jackson2JsonRedisSerializer<>(mapper, Object.class);
  }

  @Bean(name = "aiAnalysisExecutor")
  public Executor aiAnalysisExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(aiExecutorCorePoolSize);
    exec.setMaxPoolSize(aiExecutorMaxPoolSize);
    exec.setQueueCapacity(aiExecutorQueueCapacity);
    exec.setThreadNamePrefix("ai-analysis-");
    exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    exec.initialize();
    return exec;
  }

  @Value("${seustock.ai.yolo.base-url:http://localhost:8000}")
  private String yoloBaseUrl;

  @Value("${seustock.ai.yolo.connect-timeout-seconds:5}")
  private int yoloConnectTimeoutSeconds;

  @Value("${seustock.ai.yolo.read-timeout-seconds:30}")
  private int yoloReadTimeoutSeconds;

  @Bean("yoloRestClient")
  public RestClient yoloRestClient(RestClient.Builder restClientBuilder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(yoloConnectTimeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(yoloReadTimeoutSeconds));
    return restClientBuilder.clone().requestFactory(factory).baseUrl(yoloBaseUrl).build();
  }

  @Bean
  public OllamaApi ollamaApi() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(ollamaConnectTimeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(ollamaReadTimeoutSeconds));
    return OllamaApi.builder()
        .baseUrl(ollamaBaseUrl)
        .restClientBuilder(RestClient.builder().requestFactory(factory))
        .build();
  }

  @Bean
  public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource);
    return bean;
  }
}
