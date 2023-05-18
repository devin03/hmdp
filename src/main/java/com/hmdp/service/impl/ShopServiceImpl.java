package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisCacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisCacheClient redisCacheClient;

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        log.info("ShopServiceImpl queryById param id is {}", id);
        // 利用缓存空值解决缓存穿透问题
//        Shop shop = redisCacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        // 使用逻辑过期时间解决缓存击穿问题
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = redisCacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, 20L, TimeUnit.SECONDS, this::getById);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 使用逻辑过期时间解决缓存击穿问题
     * @param id 店铺id
     * @return Shop
     * @author wangdongming
     * @date 2023/04/10
     */
    private Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1.从redis中获取店铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断店铺信息是否存在
        if (StringUtils.isBlank(shopJson)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，先把json反序列化成对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 5.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 5.2.已过期，进行缓存重建
        // 6.重建缓存
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean tryLock = tryLock(lockKey);
        // 6.2.判断是否获取互斥锁成功
        if (tryLock) {
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的店铺信息
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id 店铺id
     * @return Shop
     * @author wangdongming
     * @date 2023/04/10
     */
    private Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }
        String key = LOCK_SHOP_KEY + id;
        try {
            boolean tryLock = tryLock(key);
            if (!tryLock) {
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            Shop shop = getById(id);
            if (shop == null) {
                // 缓存穿透解决方案：缓存空对象
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(key);
        }
    }

    /**
     * 利用缓存空值解决缓存穿透问题
     * @param id 店铺id
     * @return Shop
     * @author wangdongming
     * @date 2023/04/10
     */
    private Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1.从redis中获取店铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断店铺信息是否存在
        if (StringUtils.isNotBlank(shopJson)) {
            // 3.存在，直接返回店铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 4.不存在，命中空对象，直接返回null
        if (shopJson != null) {
            return null;
        }
        // 5.从数据库获取店铺信息
        Shop shop = getById(id);
        // 6.判断店铺信息是否存在
        if (shop == null) {
            // 不存在， 缓存穿透解决方案：缓存空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 7.将店铺信息放入redis中
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8.返回
        return shop;
    }

    private boolean tryLock(String key) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 将店铺信息含有逻辑过期时间存放入redis
     * eg: 可以利用单元测试模拟提前将数据放入redis
     * @param id 店铺id
     * @param expireSecond 逻辑过期时间
     * @author wangdongming
     * @date 2023/04/11
     */
    public void saveShopToRedis(Long id, Long expireSecond) throws InterruptedException {
        // 1.获取店铺信息
        Shop shop = getById(id);
        // 模拟延迟
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(expireSecond);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(localDateTime);
        // 3.放入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateShopById(Shop shop) {
        log.info("ShopServiceImpl queryById param shop is {}", JSONUtil.toJsonStr(shop));
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis，按照距离排序 分页  结果：shopId, distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //4.解析id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            //没有下页
            return Result.ok(Collections.emptyList());
        }
        //4.1 截取 from ~ end
        List<Long> idList = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        //跳过，从from开始截取
        list.stream().skip(from).forEach(result -> {
            // 4.2 获取店铺id
            String shopIdStr = result.getContent().getName();
            idList.add(Long.valueOf(shopIdStr));
            // 4.3 获取店铺距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //5.根据id查询shop
        String join = StrUtil.join(",", idList);
        List<Shop> shopList = query().in("id", idList).last("order by field(id, " + join + ")").list();
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返回
        return Result.ok(shopList);
    }


}
