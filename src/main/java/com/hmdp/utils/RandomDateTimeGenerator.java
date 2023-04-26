package com.hmdp.utils;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.RandomUtil;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class RandomDateTimeGenerator {
    public static void main(String[] args) {
        //generateDateTime();

        //生成1个随机汉字网络昵称，最大长度为8个汉字
        for (int i = 0; i < 10; i++) {
            //int randomInt = RandomUtil.randomInt(2, 6);
            //String nickName = RandomSource.personInfoSource().randomChineseNickName(randomInt);
            //System.out.println(nickName);
            //String niceName = generateChineseNiceName();
            //System.out.println(hideMiddleChars(niceName));
            //System.out.println(getStringRandom(5));
            //随机生成1条中文短句
            //System.out.println(RandomSource.languageSource().randomChineseSentence());
            System.out.println(date2());
        }

    }

    public static String date2() {
        Random random = new Random();
        // 生成当前时间前7个小时的时间
        LocalDateTime start = LocalDateTime.now().minusHours(7);
        // 生成随机时间
        LocalDateTime randomDateTime = start.plusSeconds(random.nextInt(7 * 60 * 60));
        // 输出结果
        return LocalDateTimeUtil.format(randomDateTime, "yyyy-MM-dd HH:mm:ss");
    }

    public static String date1() {
        Random random = new Random();
        Calendar calendar = Calendar.getInstance();
        Date now = new Date();
        calendar.setTime(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int sec = calendar.get(Calendar.SECOND);

        // 生成当前时间前7个小时的时间戳
        long start = now.getTime() - 7 * 60 * 60 * 1000;

        // 生成随机时间戳
        long randomTimestamp = start + random.nextInt(7 * 60 * 60 * 1000);

        // 转换为日期时间
        Date randomDate = new Date(randomTimestamp);
        Calendar randomCalendar = Calendar.getInstance();
        randomCalendar.setTime(randomDate);
        int randomYear = randomCalendar.get(Calendar.YEAR);
        int randomMonth = randomCalendar.get(Calendar.MONTH) + 1;
        int randomDay = randomCalendar.get(Calendar.DAY_OF_MONTH);
        int randomHour = randomCalendar.get(Calendar.HOUR_OF_DAY);
        int randomMin = randomCalendar.get(Calendar.MINUTE);
        int randomSec = randomCalendar.get(Calendar.SECOND);

        // 输出结果
        String dateStr = randomYear + "年" + randomMonth + "月" + randomDay + "日 " + randomHour + "时" + randomMin + "分" + randomSec + "秒";
        System.out.println("随机生成的日期时间：");
        System.out.println(dateStr);
        return dateStr;
    }

    public static String hideMiddleChars(String str) {
        int len = str.length();
        if (len <= 2) {
            return str;
        } else {
            String firstChar = str.substring(0, 1);
            String lastChar = str.substring(len - 1);
            String middleChars = str.substring(1, len - 1).replaceAll(".", "*");
            return firstChar + middleChars + lastChar;
        }
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

    public static String generateChineseNiceName() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        // 昵称长度在2到6之间随机
        int len = RandomUtil.randomInt(3, 6);
        for (int i = 0; i < len; i++) {
            // 高位码
            int highPos = (176 + Math.abs(random.nextInt(39)));
            // 低位码
            int lowPos = (161 + Math.abs(random.nextInt(93)));
            byte[] bArr = new byte[2];
            bArr[0] = (new Integer(highPos).byteValue());
            bArr[1] = (new Integer(lowPos).byteValue());
            String word = null;
            try {
                // 使用GBK编码构建汉字
                word = new String(bArr, "GBK");
            } catch (Exception e) {
                e.printStackTrace();
            }
            sb.append(word);
        }
        return sb.toString();
    }

}
