-- 这里的KEYS[1]就是锁的key，ARGV[1] 就是当前线程标识（lua脚本数组下标是从1开始的）
-- 获取锁中的线程标识，判断是否与当前线程标识一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- 一致，删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不一致，直接返回0
return 0