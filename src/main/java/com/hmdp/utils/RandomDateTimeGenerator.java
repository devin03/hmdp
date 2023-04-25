package com.hmdp.utils;

import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class RandomDateTimeGenerator {
    public static void main(String[] args) {
        generateDateTime();
    }

    private static void generateDateTime() {
        // 获取当天的开始时间和结束时间
        LocalTime startOfDay = LocalTime.of(7, 30);
        LocalTime endOfDay = LocalTime.now();

        // 生成一个随机日期和时间
        Random random = new Random();
        long seconds = startOfDay.until(endOfDay, ChronoUnit.SECONDS);
        LocalTime randomTime = startOfDay.plusSeconds(random.nextInt((int) seconds));
        LocalDateTime randomDateTime = LocalDateTime.now().withHour(randomTime.getHour())
                                                          .withMinute(randomTime.getMinute())
                                                          .withSecond(randomTime.getSecond());

        System.out.println("随机日期和时间：" + randomDateTime);
        System.out.println("随机日期和时间：" + LocalDateTimeUtil.format(randomDateTime, "yyyy-MM-dd HH:mm:ss"));
    }
}
