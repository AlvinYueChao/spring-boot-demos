package org.example.alvin.redis.redisbase.advtypes;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

@Component
public class RedisBloomFilter {

  public static final String RS_BF_NS = "rbf:";
  /**
   * 预估元素数量
   */
  private final int numApproxElements;
  /**
   * 可接受的最大误差
   */
  private final double fpp;
  /**
   * 自动计算的hash函数个数
   */
  private final int numHashFunctions;
  /**
   * 自动计算的最优Bitmap长度
   */
  private final int bitmapLength;

  @Autowired
  private JedisPool jedisPool;

  public RedisBloomFilter(int numApproxElements, double fpp) {
    this.numApproxElements = numApproxElements;
    this.fpp = fpp;
    this.bitmapLength = (int) Math.floor(- this.numApproxElements * Math.log(fpp) / Math.pow(Math.log(2), 2));
    this.numHashFunctions = Math.max(1, (int) Math.round(Math.log(2) * this.bitmapLength / this.numApproxElements));
  }

  private long[] getBitIndices(String element) {
    long[] indices = new long[this.numHashFunctions];
    byte[] bytes = Hashing.murmur3_128().hashObject(element, Funnels.stringFunnel(StandardCharsets.UTF_8)).asBytes();
    long hash1 = Longs.fromBytes(bytes[15], bytes[14], bytes[13], bytes[12], bytes[11], bytes[10], bytes[9], bytes[8]);
    long hash2 = Longs.fromBytes(bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]);

    long combinedHash = hash1;
    for (int i = 0; i < this.numHashFunctions; i++) {
      indices[i] = (combinedHash & Long.MAX_VALUE) % this.bitmapLength;
      combinedHash += hash2;
    }
    return indices;
  }
}
