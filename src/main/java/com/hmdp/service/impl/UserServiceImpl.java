package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;

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
        // 4.验证码放入session
        session.setAttribute("code", code);
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
        String cacheCode = (String) session.getAttribute("code");
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
        // 6.用户信息放入session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 7.返回
        return Result.ok();
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
