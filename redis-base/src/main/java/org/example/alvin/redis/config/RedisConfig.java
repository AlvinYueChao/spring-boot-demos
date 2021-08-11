package org.example.alvin.redis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

  @Value("${spring.redis.host}")
  private String host;

  @Value("${spring.redis.port}")
  private int port;

  @Value("${spring.redis.jedis.pool.max-idle}")
  private int maxIdle;

  @Value("${spring.redis.jedis.pool.max-active}")
  private int maxActive;

  @Value("${spring.redis.jedis.pool.max-wait}")
  private long maxWait;

  @Value("${spring.redis.jedis.pool.min-idle}")
  private int minIdle;

  @Bean
  public JedisPool jedisPool() {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxIdle(maxIdle);
    poolConfig.setMaxWaitMillis(maxWait);
    poolConfig.setMaxTotal(maxActive);
    poolConfig.setMinIdle(minIdle);
    return new JedisPool(poolConfig, host, port);
  }
}
