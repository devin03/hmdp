package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券抢购
     * @param voucherId 优惠券id
     * @return Result
     * @author wangdongming
     * @date 2023/04/15
     */
    Result seckillVoucher(Long voucherId) throws InterruptedException;

    /**
     * 订单处理
     * @param voucherOrder 优惠券订单信息
     * @author wangdongming
     * @date 2023/04/15
     */
    void orderHandle(VoucherOrder voucherOrder);
}
