package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import com.apifan.common.random.RandomSource;
import com.apifan.common.random.util.ResourceUtils;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomLicensePlateGenerator {

    public static void main(String[] args) {

        Random random = new Random();
        for (int i = 0; i < 50; i++) {
            System.out.println("随机生成的车牌号为：" + generateLicenseNo());
        }


    }

    /**
     * 用于随机选的地区
     */
    public static final String[] PROVINCES = {
            "京", "津", "沪", "渝", "蒙", "新", "藏", "宁", "桂", "港", "云",
            "澳", "黑", "吉", "辽", "晋", "冀", "青", "鲁", "豫", "苏", "贵",
            "皖", "浙", "闽", "赣", "湘", "鄂", "粤", "琼", "甘", "陕", "川", "台"
    };
    /**
     * 用于随机选的数字
     */
    public static final String BASE_NUMBER = "0123456789";
    /**
     * 用于随机选的字符
     */
    public static final String BASE_CHAR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";


    private static String generateLicenseNo() {
        int length = 5;
        StringBuilder licenseNo = new StringBuilder();
        Random random = new Random();
        // 生成省份
        int index = random.nextInt(PROVINCES.length);
        licenseNo.append(PROVINCES[index]);
        // 生成省份后面跟随的字母
        licenseNo.append(BASE_CHAR.charAt(random.nextInt(BASE_CHAR.length())));
        List<String> number = new ArrayList<>(length);
        //最多2个字母
        int alphaCnt = RandomUtils.nextInt(0, 3);
        if (alphaCnt > 0) {
            for (int i = 0; i < alphaCnt; i++) {
                number.add(String.valueOf(BASE_CHAR.charAt(random.nextInt(BASE_CHAR.length()))));
            }
        }
        //剩余部分全是数字
        int numericCnt = length - alphaCnt;
        for (int i = 0; i < numericCnt; i++) {
            number.add(String.valueOf(BASE_NUMBER.charAt(random.nextInt(BASE_NUMBER.length()))));
        }
        //打乱字符顺序
        Collections.shuffle(number);
        licenseNo.append(Joiner.on("").join(number));
        return licenseNo.toString();
    }

}
