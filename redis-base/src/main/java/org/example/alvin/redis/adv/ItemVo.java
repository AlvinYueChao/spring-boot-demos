package org.example.alvin.redis.adv;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

public class ItemVo<T> implements Delayed {

  @Getter
  private final long expireAtTimestamp;
  @Getter
  private final T data;

  // 提前100毫秒进行续期
  public ItemVo(long ttlMilliseconds, T data) {
    super();
    this.expireAtTimestamp = ttlMilliseconds + System.currentTimeMillis() - 100;
    this.data = data;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return unit.convert(Duration.ofMillis(this.expireAtTimestamp - System.currentTimeMillis()));
  }

  @Override
  public int compareTo(Delayed o) {
    long millisecondsDiff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
    return Long.compare(millisecondsDiff, 0L);
  }
}
