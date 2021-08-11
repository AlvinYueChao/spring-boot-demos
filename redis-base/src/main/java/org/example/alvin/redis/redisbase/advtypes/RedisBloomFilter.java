package org.example.alvin.redis.redisbase.advtypes;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

@Component
public class RedisBloomFilter {

  private final static Logger LOGGER = LoggerFactory.getLogger(RedisBloomFilter.class);

  public static final String RS_BF_NS = "rbf:";
  /**
   * 预估元素数量
   */
  private int numApproxElements;
  /**
   * 可接受的最大误差
   */
  private double fpp;
  /**
   * 自动计算的hash函数个数
   */
  private int numHashFunctions;
  /**
   * 自动计算的最优Bitmap长度
   */
  private int bitmapLength;

  @Autowired
  private JedisPool jedisPool;

  public void init(int numApproxElements, double fpp) {
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
    LOGGER.info("数组下标: {}", indices);
    return indices;
  }

  public void insert(String key, String element, long expireSec) {
    if (StringUtils.isEmpty(key) || StringUtils.isEmpty(element)) {
      throw new IllegalArgumentException("键值均不能为空");
    }
    String actualKey = RS_BF_NS.concat(key);
    try (Jedis jedis = jedisPool.getResource()) {
      try (Pipeline pipeline = jedis.pipelined()) {
        for (long index : getBitIndices(element)) {
          pipeline.setbit(actualKey, index, true);
        }
        pipeline.syncAndReturnAll();
      } catch (Exception e) {
        LOGGER.error("插入位图发生异常.", e);
      }
      jedis.expire(actualKey, expireSec);
    }
  }

  public boolean mayExist(String key, String element) {
    if (StringUtils.isEmpty(key) || StringUtils.isEmpty(element)) {
      throw new IllegalArgumentException("键值均不能为空");
    }
    String actualKey = RS_BF_NS.concat(key);
    boolean result = false;
    try (Jedis jedis = jedisPool.getResource()) {
      try (Pipeline pipeline = jedis.pipelined()) {
        for (long index : getBitIndices(element)) {
          pipeline.getbit(actualKey, index);
        }
        result = !pipeline.syncAndReturnAll().contains(false);
      } catch (Exception e) {
        LOGGER.error("查询位图发生异常", e);
      }
    }
    return result;
  }

  public void cleanup(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      try (Pipeline pipeline = jedis.pipelined()) {
        Response<Boolean> exists = pipeline.exists(key);
        if (exists.get()) {
          pipeline.del(key);
        }
        pipeline.syncAndReturnAll();
      } catch (Exception e) {
        LOGGER.error("删除键发生异常", e);
      }
    }
  }

  @Override
  public String toString() {
    return String.format("RedisBloomFilter(numApproxElements=%d, fpp=%f, bitmapLength=%d, numHashFunctions=%d)", numApproxElements, fpp, bitmapLength, numHashFunctions);
  }
}
