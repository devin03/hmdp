package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
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
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        boolean lock = simpleRedisLock.tryLock(10L);
        if (!lock) {
            return Result.fail("不可以重复下单");
        }
        try {
            // 获取代理对象 代理增强
            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.orderHandle(voucherId, userId);
        } finally {
            simpleRedisLock.unlock();
        }
    }

    @Transactional
    public Result orderHandle(Long voucherId, Long userId) {
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一单");
        }
        // 6.扣减库存
        // 此处用了乐观锁，以库存作为条件，库存大于0就可以更新
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!update) {
            return Result.fail("库存扣减失败");
        }
        // 7.生成订单
        long orderId = redisIdGenerator.generateId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        // 8.返回订单id
        return Result.ok(orderId);
    }

}
