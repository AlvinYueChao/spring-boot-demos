package org.example.alvin.config;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.alvin.pojo.Foo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

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
    /*
    org.springframework.cloud.function.json.JacksonMapper.doFromJson
      if (json instanceof String) {
        convertedValue = this.mapper.readValue((String) json, constructType);
      }
     */
    return value -> {
      logger.info("--- invoked word(), parameters: {}", value);
      return Arrays.asList(value.getValue().split(","));
    };
  }

  @Bean
  public Function<Flux<Foo>, Flux<List<String>>> fluxWords() {
    /*
    org.springframework.cloud.function.json.JacksonMapper.doFromJson
      if (json instanceof String) {
        convertedValue = this.mapper.readValue((String) json, constructType);
      }
     */
    return request -> request.log().map(x -> {
      logger.info("-- invoked fluxWords(), parameters: {}", x);
      return Arrays.asList(x.getValue().split(","));
    });
  }
}
