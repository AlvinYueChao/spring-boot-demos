package org.example.alvin.redis.adv;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Component
public class RedisDistLock implements Lock {

  private static final Logger LOGGER = LogManager.getLogger(RedisDistLock.class);

  private static final int LOCK_TIME = 5 * 1000;
  private static final String RS_DISTLOCK_NS = "tdln";
  private static final String RELEASE_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
  /**
   * 保存每个线程独有的锁ID
   */
  private final ThreadLocal<String> lockerId = new ThreadLocal<>();
  /**
   * 解决锁重入问题
   */
  @Getter @Setter
  private Thread ownerThread;
  @Getter @Setter
  private String lockName = "lock";

  @Autowired
  private JedisPool jedisPool;

  @Override
  public void lock() {
    while (!tryLock()) {
      try {
        Thread.sleep(100);
        LOGGER.info("获取锁成功，执行其他业务代码...");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warn("执行业务方法被打断", e);
      }
    }
  }

  @Override
  public void lockInterruptibly() throws InterruptedException {
    throw new UnsupportedOperationException("不支持可中断获取锁!");
  }

  @Override
  public boolean tryLock() {
    boolean result;
    Thread currentThread = Thread.currentThread();
    if (ownerThread == currentThread) {
      // 锁重入
      result = true;
    } else if (ownerThread != null){
      // 其他线程持有锁
      result = false;
    } else {
      // 进入分布式锁竞争
      try (Jedis jedis = this.jedisPool.getResource()) {
        String id = UUID.randomUUID().toString();
        SetParams setParams = new SetParams();
        setParams.px(LOCK_TIME);
        setParams.nx();
        // 首先进行本地抢锁，避免请求redis拿锁的网络开销
        synchronized (this) {
          String response = jedis.set(RS_DISTLOCK_NS + lockName, id, setParams);
          // 双重检测，避免不必要的网络抢锁开销
          if (ownerThread == null && "OK".equalsIgnoreCase(response)) {
            lockerId.set(id);
            setOwnerThread(currentThread);
            result = true;
          } else {
            result = false;
          }
        }
      } catch (Exception e) {
        LOGGER.warn("抢锁失败", e);
        result = false;
      }
    }
    return result;
  }

  @Override
  public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException("不支持等待尝试获取锁!");
  }

  @Override
  public void unlock() {
    if (ownerThread != Thread.currentThread()) {
      throw new RuntimeException("试图释放被别的线程持有的锁!");
    }
    try (Jedis jedis = this.jedisPool.getResource()) {
      Integer result = (Integer) jedis.eval(RELEASE_LOCK_LUA, Collections.singletonList(RS_DISTLOCK_NS + lockName), Collections.singletonList(lockerId.get()));
      if (result == null || result != 0) {
        LOGGER.info("Redis上的锁已经释放");
      } else {
        LOGGER.warn("Redis上的锁释放失败");
      }
    } catch (Exception e) {
      LOGGER.warn("Redis上的锁释放失败");
    } finally {
      lockerId.remove();
      setOwnerThread(null);
      LOGGER.info("本地锁已释放");
    }
  }

  @Override
  public Condition newCondition() {
    throw new UnsupportedOperationException("不支持等待通知操作!");
  }
}