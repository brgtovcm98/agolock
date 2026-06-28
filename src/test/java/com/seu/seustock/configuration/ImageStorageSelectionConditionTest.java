package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.seu.seustock.mapper.ImageMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.service.ImageFileValidator;
import com.seu.seustock.service.ImageStorageService;
import com.seu.seustock.service.LocalImageStorageService;
import com.seu.seustock.service.MinioImageStorageService;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ImageStorageSelectionConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              MockDependenciesConfig.class,
              MinioConfig.class,
              MinioImageStorageService.class,
              LocalImageStorageService.class);

  @Test
  void defaultStorageUsesMinio() {
    contextRunner
        .withPropertyValues(
            "seustock.minio.endpoint=http://localhost:9000",
            "seustock.minio.access-key=test",
            "seustock.minio.secret-key=testpassword")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ImageStorageService.class);
              assertThat(context).hasSingleBean(MinioImageStorageService.class);
              assertThat(context).doesNotHaveBean(LocalImageStorageService.class);
              assertThat(context).hasSingleBean(MinioClient.class);
            });
  }

  @Test
  void localStorageDoesNotRequireMinioConfiguration() {
    contextRunner
        .withPropertyValues("seustock.image-storage.type=local")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ImageStorageService.class);
              assertThat(context).hasSingleBean(LocalImageStorageService.class);
              assertThat(context).doesNotHaveBean(MinioImageStorageService.class);
              assertThat(context).doesNotHaveBean(MinioClient.class);
            });
  }

  @Configuration
  static class MockDependenciesConfig {
    @Bean
    ImageMapper imageMapper() {
      return mock(ImageMapper.class);
    }

    @Bean
    UserMapper userMapper() {
      return mock(UserMapper.class);
    }

    @Bean
    MessageSource messageSource() {
      return mock(MessageSource.class);
    }

    @Bean
    ImageFileValidator imageFileValidator() {
      return new ImageFileValidator(messageSource());
    }
  }
}
