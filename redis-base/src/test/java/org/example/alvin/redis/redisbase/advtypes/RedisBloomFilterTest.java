package org.example.alvin.redis.redisbase.advtypes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisBloomFilterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisBloomFilterTest.class);

  private final static int DSY_SEC = 24 * 60 * 60;

  @Autowired
  private RedisBloomFilter redisBloomFilter;

  @Test
  void testInsert() {
    redisBloomFilter.init(3000, 0.03);
    LOGGER.info("{}", redisBloomFilter);
    redisBloomFilter.insert("topic_read:8839540:20210812", "76930242", DSY_SEC);
    redisBloomFilter.insert("topic_read:8839540:20210812", "76930243", DSY_SEC);
    redisBloomFilter.insert("topic_read:8839540:20210812", "76930244", DSY_SEC);
    redisBloomFilter.insert("topic_read:8839540:20210812", "76930245", DSY_SEC);
    redisBloomFilter.insert("topic_read:8839540:20210812", "76930246", DSY_SEC);

    Assertions.assertTrue(redisBloomFilter.mayExist("topic_read:8839540:20210812", "76930242"));
    Assertions.assertTrue(redisBloomFilter.mayExist("topic_read:8839540:20210812", "76930243"));
    Assertions.assertTrue(redisBloomFilter.mayExist("topic_read:8839540:20210812", "76930244"));
    Assertions.assertTrue(redisBloomFilter.mayExist("topic_read:8839540:20210812", "76930245"));
    Assertions.assertFalse(redisBloomFilter.mayExist("topic_read:8839540:20210812", "76930250"));
  }
}
