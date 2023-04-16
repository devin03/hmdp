package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
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
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long time) {
        //获取线程id，作为value值
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean lockFlag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lockFlag);
    }

    @Override
    public void unlock() {
        //获取线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取redis中的id值
        String id = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + name);
        //判断是否一致，解决锁误删的问题
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(LOCK_PREFIX + name);
        }
    }
}
