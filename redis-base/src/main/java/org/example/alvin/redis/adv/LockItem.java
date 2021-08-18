package org.example.alvin.redis.adv;

import lombok.Getter;

public class LockItem {
  @Getter
  private final String key;
  @Getter
  private final String value;

  public LockItem(String key, String value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public String toString() {
    return String.format("LockItem[key=%s, value=%s]", this.key, this.value);
  }
}
