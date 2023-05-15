package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisIdGenerator redisIdGenerator;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService voucherOrderService;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final ExecutorService SECKILL_EXECUTOR = Executors.newSingleThreadExecutor();

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * @PostConstruct
     * 该方法是该类初始化完成后就会执行
     */
    @PostConstruct
    private void init() {
        SECKILL_EXECUTOR.submit(new VoucherOrderHandle());
    }

    private class VoucherOrderHandle implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true) {
                try {
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，则表示没有消息，继续下一个循环
                        continue;
                    }
                    //3.解析消息
                    MapRecord<String, Object, Object> mapRecord = list.get(0);
                    Map<Object, Object> map = mapRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                    //4.如果获取成功，则表示可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认  SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", mapRecord.getId());
                } catch (Exception e) {
                    log.error("orderTask occur error", e);
                    //如果出现异常，对pending-list里面的消息进行处理
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true) {
                try {
                    //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，则表示pending-list没有异常消息，结束循环
                        break;
                    }
                    //3.解析消息
                    MapRecord<String, Object, Object> mapRecord = list.get(0);
                    Map<Object, Object> map = mapRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                    //4.如果获取成功，则表示可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认  SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", mapRecord.getId());
                } catch (Exception e) {
                    log.error("orderTask pending-list occur error", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

    }



    /*private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandle implements Runnable {

        @Override
        public void run() {
            while(true) {
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTask.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("orderTask occur error", e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
        //获取锁（可重入），指定锁的key
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //尝试获取所，参数分别是：获取锁的最大等待时间（期间会重试），锁自动释放时间，时间单位
        boolean lockFlag = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (!lockFlag) {
            log.error("不可以重复下单");
            return;
        }
        try {
            voucherOrderService.orderHandle(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //获取userId
        Long userId = UserHolder.getUser().getId();
        //生成订单id
        long orderId = redisIdGenerator.generateId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 2.判断结果是否为0
        int value = result.intValue();
        if (value != 0) {
            // 2.1 不为0，没有购买资格
            return Result.fail(value == 1 ? "库存不足" : "不能重复下单");
        }
        // 获取代理对象 代理增强
        //在这里获取代理对象是因为子线程不能获取代理对象
        voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
        // 3. 返回订单id
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //获取userId
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果是否为0
        int value = result.intValue();
        if (value != 0) {
            // 2.1 不为0，没有购买资格
            return Result.fail(value == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.1 为0，有购买资格，把下单信息保存到阻塞队列
        //生成订单id
        long orderId = redisIdGenerator.generateId("order");
        //保存阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        orderTask.add(voucherOrder);
        // 获取代理对象 代理增强
        //在这里获取代理对象是因为子线程不能获取代理对象
        voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
        // 3. 返回订单id
        return Result.ok(orderId);
    }*/

    /*@Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 1.根据优惠券id查询秒杀优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        // 2.判断秒杀活动是否开始
        if (voucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀活动还未开始");
        }
        // 3.判断秒杀活动是否结束
        if (voucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀活动已经结束");
        }
        // 4.判断秒杀优惠券库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        //获取锁（可重入），指定锁的key
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //尝试获取所，参数分别是：获取锁的最大等待时间（期间会重试），锁自动释放时间，时间单位
        boolean lockFlag = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (!lockFlag) {
            return Result.fail("不可以重复下单");
        }
        try {
            // 获取代理对象 代理增强
            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.orderHandle(voucherId, userId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = RuntimeException.class)
    public void orderHandle(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("用户已经购买过一单");
            return;
        }
        // 6.扣减库存
        // 此处用了乐观锁，以库存作为条件，库存大于0就可以更新
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!update) {
            log.error("库存扣减失败");
            return;
        }
        // 7.生成订单
        save(voucherOrder);
    }

}
