package org.example.alvin.redis.adv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisDistLockTest {
  private static final Logger LOGGER = LogManager.getLogger(RedisDistLockTest.class);

  @Autowired
  private RedisDistLock redisDistLock;
  private final AtomicInteger count = new AtomicInteger(0);

  @Test
  void testRedisDistLock() {
    int clientCount = 5;
    CountDownLatch countDownLatch = new CountDownLatch(clientCount);
    ExecutorService pool = Executors.newFixedThreadPool(clientCount);
    for (int i = 0; i < clientCount; i++) {
      pool.execute(() -> {
        try {
          redisDistLock.lock();
          LOGGER.info("{} 执行业务代码", Thread.currentThread().getName());
          Thread.sleep(2000);
          count.incrementAndGet();
        } catch (InterruptedException e) {
          LOGGER.warn("业务代码执行过程被打断", e);
        } finally {
          redisDistLock.unlock();
        }
        countDownLatch.countDown();
      });
    }
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      LOGGER.warn("等待业务代码执行过程被打断", e);
      LOGGER.info("执行结束的客户端数量: {}", count.get());
    }
  }
}
