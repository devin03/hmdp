package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * @author wangdongming
 * @date 2023/04/08
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从ThreadLocal中获取用户信息，不存在进行拦截
        if (UserHolder.getUser() == null) {
            // 返回401状态码
            response.setStatus(401);
            return false;
        }
        return true;
    }

}
