package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        log.info("UserServiceImpl sendCode param phone is {}", phone);
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.不符合直接返回错误信息
            return Result.fail("手机号格式不正确！");
        }
        // 3.符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4.验证码放入redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码
        log.info("UserServiceImpl sendCode success, code is {}", code);
        // 6.返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        log.info("UserServiceImpl login param loginForm is {}", JSONUtil.toJsonStr(loginForm));
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确！");
        }
        // 2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(code)) {
            // 3.验证码不符合，直接返回错误信息
            return Result.fail("验证码错误");
        }
        // 4.验证用户信息
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 5.用户不存在，注册用户
            user = createUser(phone);
        }
        // 6.用户信息放入redis
        // 6.1 使用UUID随机生成token作为redis的key
        String token = UUID.randomUUID().toString(true);
        // 6.2 获取userDTO并将之转换为HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 6.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        // 6.4 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 7.返回
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入Redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止的所有签到记录，返回的是一个十进制数字  BITFIELD key GET type offset
        List<Long> longList = stringRedisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (longList == null || longList.isEmpty()) {
            //没有签到结果
            return Result.ok(0);
        }
        Long num = longList.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        //6.循环遍历
        int count = 0;
        while (true) {
            //6.1 让数字与1做与运算，得到数字的最后一个bit位并判断bit位是否为0
            if ((num & 1) == 0) {
                //6.2 如果为0，说明未签到，结束
                break;
            }
            //6.3 如果不为0，说明已签到，计数器+1
            count++;
            //6.4 把数字右移一位，舍弃最后一个bit位，继续下一个bit位运算
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumber());
        user.setCreateTime(LocalDateTime.now());
        save(user);
        return user;
    }

}
