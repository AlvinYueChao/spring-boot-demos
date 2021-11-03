package org.example.alvin.redis.adv;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import javax.annotation.PreDestroy;
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

  private static final int LOCK_TIME = 1000;
  private static final String RS_DISTLOCK_NS = "tdln";
  private static final String RELEASE_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
  private static final String DELAY_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";
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
  /**
   * 看门狗线程，负责锁续命
   */
  private Thread expireThread;
  private static final DelayQueue<ItemVo<LockItem>> DELAY_DOG = new DelayQueue<>();

  @Autowired
  private JedisPool jedisPool;

  @Override
  public void lock() {
    while (!tryLock()) {
      try {
        Thread.sleep(100);
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
//            LOGGER.info("当前锁: {}", RS_DISTLOCK_NS + lockName);
//            LOGGER.info("当前持有锁的线程名: {}", lockerId.get());
            // 启动看门狗线程
            if (expireThread == null) {
              expireThread = new Thread(new ExpireTask(), "expireThread");
              expireThread.start();
            }
            DELAY_DOG.add(new ItemVo<>(LOCK_TIME, new LockItem(lockName, id)));
            LOGGER.info("{} 已获得锁", Thread.currentThread().getName());
            result = true;
          } else {
            LOGGER.info("{} 无法获得锁", Thread.currentThread().getName());
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
      Long result = (Long) jedis.eval(RELEASE_LOCK_LUA, Collections.singletonList(RS_DISTLOCK_NS + lockName), Collections.singletonList(lockerId.get()));
//      LOGGER.info("当前锁: {}", RS_DISTLOCK_NS + lockName);
//      LOGGER.info("当前持有锁的线程名: {}", lockerId.get());
      if (result == null || result != 0L) {
        LOGGER.info("Redis上的锁已经释放");
      } else {
        LOGGER.warn("Redis上的锁释放失败");
      }
    } catch (Exception e) {
      throw new RuntimeException("释放锁失败！",e);
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

  private class ExpireTask implements Runnable {
    private final Logger logger = LogManager.getLogger(ExpireTask.class);

    @Override
    public void run() {
      logger.info("看门狗线程已启动...");
      while (!Thread.currentThread().isInterrupted()) {
        try {
          logger.info("开始监视锁的过期情况...");
          LockItem lockItem = DELAY_DOG.take().getData();
          logger.info("Redis上的锁 {} 准备续期...", lockItem);
          try (Jedis jedis = jedisPool.getResource()) {
            Long result = (Long) jedis.eval(DELAY_LOCK_LUA, Collections.singletonList(RS_DISTLOCK_NS + lockItem.getKey()),
                Arrays.asList(lockItem.getValue(), String.valueOf(LOCK_TIME)));
//            logger.info("锁续命的key: {}", RS_DISTLOCK_NS + lockItem.getKey());
//            logger.info("续命的线程名: {}, 续命时间: {}ms", lockItem.getValue(), String.valueOf(LOCK_TIME));
            if (result == null || result == 0) {
              logger.info("Redis上的锁已释放，无需续期!");
//              break;
            } else {
              DELAY_DOG.add(new ItemVo<>(LOCK_TIME, new LockItem(lockItem.getKey(), lockItem.getValue())));
              logger.info("Redis上的锁还未释放，重新进入待续期检查");
            }
          } catch (Exception e) {
            throw new RuntimeException("锁续期失败!", e);
          }
        } catch (InterruptedException e) {
          logger.warn("看门狗线程被中断", e);
          break;
        }
      }
      logger.info("看门狗线程准备关闭...");
    }
  }

  @PreDestroy
  public void closeExpireThread() {
    if (expireThread != null) {
      expireThread.interrupt();
    }
  }
}