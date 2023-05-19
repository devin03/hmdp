package com.devin;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author wangdongming
 * @date 2023/04/14
 */
@SpringBootTest
public class HmdpTest {

    @Resource
    private RedisIdGenerator redisIdGenerator;
    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(300);

    @Test
    void test() throws InterruptedException {
        String keyPrefix = "order";
        long begin = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdGenerator.generateId(keyPrefix);
                System.out.println(id);
            }
            latch.countDown();
        };

        for (int i = 0; i < 300; i++) {
            EXECUTOR.submit(runnable);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end-begin);
    }

    @Test
    public void loadShopData() {
        //1.查询店铺信息
        List<Shop> shopList = shopService.list();
        //2.把店铺按照typeId分组，typeId一致的放在一个集合  key:typeId  value:店铺集合
        Map<Long, List<Shop>> listMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入redis中
        //String key = "shop:geo:";
        //for (Shop shop : shopList) {
        //    key = key + shop.getTypeId();
        //    stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
        //}
        for (Map.Entry<Long, List<Shop>> entry : listMap.entrySet()) {
            //3.1 获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            //3.2 获取同类型的店铺集合
            List<Shop> list = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocationList = new ArrayList<>(list.size());
            //3.3 写入redis  GEOADD key 经度 维度 member
            for (Shop shop : list) {
                //stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                geoLocationList.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, geoLocationList);
        }

    }

    @Test
    public void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl", values);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hl");
        System.out.println(size);
    }

}
