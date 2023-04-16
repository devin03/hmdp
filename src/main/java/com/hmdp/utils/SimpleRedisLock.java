package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author wangdongming
 * @date 2023/04/16
 */
public class SimpleRedisLock implements RedisLock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public static final String LOCK_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long time) {
        //获取线程id，作为value值
        long threadId = Thread.currentThread().getId();
        Boolean lockFlag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId + "", time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lockFlag);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(LOCK_PREFIX + name);
    }
}
