package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author wangdongming
 * @date 2023/04/12
 */
@Slf4j
@Component
public class RedisCacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public RedisCacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 数据存放入redis，含有TTL过期时间
     * @param key key值
     * @param value value值
     * @param time TTL过期时间
     * @param timeUnit TTL过期时间单位
     * @date 2023/04/12
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 含有逻辑过期时间的缓存存储
     * @param key key值
     * @param value value值
     * @param time TTL过期时间
     * @param timeUnit TTL过期时间单位
     * @date 2023/04/12
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 写入redis，不含TTL，永久有效
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 利用缓存空值解决缓存穿透问题
     * @author wangdongming
     * @date 2023/04/10
     */
    public  <R, T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Function<T, R> fallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中获取信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存数据是否存在
        if (StringUtils.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 4.不存在，命中空对象，直接返回null
        if (json != null) {
            return null;
        }
        // 5.从数据库获取信息
        R r = fallback.apply(id);
        // 6.判断信息是否存在
        if (r == null) {
            // 不存在， 缓存穿透解决方案：缓存空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 7.将信息放入redis中
        this.set(key, r, time, unit);
        // 8.返回
        return r;
    }

    /**
     * 使用逻辑过期时间解决缓存击穿问题
     * @author wangdongming
     * @date 2023/04/10
     */
    public <R, T> R queryWithLogicalExpire(String keyPrefix, T id, Class<R> type, String lockKeyPrefix, Long time, TimeUnit unit, Function<T, R> fallback) {
        String key = keyPrefix + id;
        // 1.从redis中获取信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断信息是否存在
        if (StringUtils.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，先把json反序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 5.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回
            return r;
        }
        // 5.2.已过期，进行缓存重建
        // 6.重建缓存
        // 6.1.获取互斥锁
        String lockKey = lockKeyPrefix + id;
        boolean tryLock = tryLock(lockKey);
        // 6.2.判断是否获取互斥锁成功
        if (tryLock) {
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = fallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的店铺信息
        return r;
    }

    private boolean tryLock(String key) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
