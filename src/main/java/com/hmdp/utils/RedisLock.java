package com.hmdp.utils;

/**
 * redis锁接口
 * @author wangdongming
 * @date 2023/04/16
 */
public interface RedisLock {

    /**
     * 尝试获取锁
     * @param time 超时时间
     * @return boolean
     * @author wangdongming
     * @date 2023/04/16
     */
    boolean tryLock(Long time);

    /**
     * 释放锁
     * @author wangdongming
     * @date 2023/04/16
     */
    void unlock();

}
