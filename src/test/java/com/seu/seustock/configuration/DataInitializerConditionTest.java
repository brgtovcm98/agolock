package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ItemImageMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockImageMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.StockTransactionMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataInitializerConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(MockDependenciesConfig.class, DataInitializer.class);

  @Test
  void beanIsAbsentWhenPropertyMissing() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(DataInitializer.class));
  }

  @Test
  void beanIsAbsentWhenPropertyFalse() {
    contextRunner
        .withPropertyValues("seustock.datainit.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(DataInitializer.class));
  }

  @Test
  void beanIsPresentWhenPropertyTrue() {
    contextRunner
        .withPropertyValues("seustock.datainit.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(DataInitializer.class));
  }

  @Configuration
  static class MockDependenciesConfig {
    @Bean
    UserMapper userMapper() {
      return mock(UserMapper.class);
    }

    @Bean
    SpaceMapper spaceMapper() {
      return mock(SpaceMapper.class);
    }

    @Bean
    ShelfMapper shelfMapper() {
      return mock(ShelfMapper.class);
    }

    @Bean
    BoxMapper boxMapper() {
      return mock(BoxMapper.class);
    }

    @Bean
    ItemMapper itemMapper() {
      return mock(ItemMapper.class);
    }

    @Bean
    StockMapper stockMapper() {
      return mock(StockMapper.class);
    }

    @Bean
    StockTransactionMapper stockTransactionMapper() {
      return mock(StockTransactionMapper.class);
    }

    @Bean
    ItemImageMapper itemImageMapper() {
      return mock(ItemImageMapper.class);
    }

    @Bean
    StockImageMapper stockImageMapper() {
      return mock(StockImageMapper.class);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return mock(PasswordEncoder.class);
    }

    @Bean
    ImageStorageService imageStorageService() {
      return mock(ImageStorageService.class);
    }
  }
}
