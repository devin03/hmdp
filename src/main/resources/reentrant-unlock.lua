-- 释放锁的lua脚本
-- 锁的key
local key = KEYS[1];
-- 线程唯一标识
local threadId = ARGV[1];
-- 锁的自动释放时间
local releaseTime = ARGV[2];
-- 判断当前锁是否还是被自己持有
if(redis.call('hexists', key, threadId) == 0) then
    -- 不是自己的，直接返回
    return nil;
end;
-- 是自己的锁，则重入次数-1
local count = redis.call('hincrby', key, threadId, -1);
-- 判断重入次数是否为0
if(count > 0) then
    -- 大于0说明不能释放锁，重置有效期返回
    redis.call('expire', key, releaseTime);
    return nil;
else
    -- 等于0说明可以释放锁，直接删除
    redis.call('del', key);
    return nil;
end;