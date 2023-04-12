package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * redis缓存数据，用于逻辑时间过期处理
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
