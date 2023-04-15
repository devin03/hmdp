package com.devin;

import com.hmdp.utils.RedisIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author wangdongming
 * @date 2023/04/14
 */
@SpringBootTest
public class HmdpTest {

    @Resource
    private RedisIdGenerator redisIdGenerator;

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

}
