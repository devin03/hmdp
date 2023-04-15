package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author wangdongming
 * @date 2023/04/13
 */
@Component

public class RedisIdGenerator {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 开始时间戳
     */
    public static final long BEGIN_TIMESTAMP = 1640966400L;
    public static final int COUNT_BITS = 32;

    /**
     * 利用redis生成id
     * 规则：时间戳 + 序列号
     * @param keyPrefix id生成前缀key
     * @return long
     * @author wangdongming
     * @date 2023/04/13
     */
    public long generateId(String keyPrefix) {
        //时间戳
        long nowSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        //序列号
        //获取当前日期，精确到天
        String format = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + format);
        //拼接id返回
        return timestamp << COUNT_BITS | increment;
    }

}
