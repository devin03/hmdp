package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author wangdongming
 * @date 2023/04/08
 */
@Configuration
public class MyConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/log",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "voucher/**"
        );
    }

}
