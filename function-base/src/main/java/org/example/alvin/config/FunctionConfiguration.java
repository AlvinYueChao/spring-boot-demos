package org.example.alvin.config;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.alvin.pojo.Foo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FunctionConfiguration {

  private final Logger logger = LogManager.getLogger(FunctionConfiguration.class);

  @Bean
  public Function<String, String> reverseString() {
    return value -> new StringBuilder(value).reverse().toString();
  }

  @Bean
  public Function<String, String> uppercase() {
    return String::toUpperCase;
  }

  @Bean
  public Function<Foo, List<String>> words() {
    return value -> {
      logger.info("--- invoked word(), parameters: {}", value);
      return Arrays.asList(value.getValue().split(","));
    };
  }
}
