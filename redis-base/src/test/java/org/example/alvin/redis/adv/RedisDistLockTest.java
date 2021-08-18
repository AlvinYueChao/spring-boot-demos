package org.example.alvin.redis.adv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisDistLockTest {

  @Autowired
  private RedisDistLock redisDistLock;

  @Test
  void testRedisDistLock() {

  }
}
