package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.getBlogUserInfo(blog);
            this.isLikedBlog(blog);
        });
        return Result.ok(records);
    }



    @Override
    public Result queryBlogById(Long id) {
        //1.查询博客
        Blog blog = getById(id);
        //2.查询博客用户信息
        getBlogUserInfo(blog);
        //3.判断用户是否已点赞过
        isLikedBlog(blog);
        return Result.ok(blog);
    }

    private void isLikedBlog(Blog blog) {
        //1.获取登录用户信息
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点过赞
        String key = "blog:liked:" + blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户信息
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点过赞
        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isMember)) {
            //3.如果未点赞，可以点赞
            //3.1 数据库点赞量+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                //3.2 保存用户到redis中
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        }else {
            //4.如果已点赞，取消点赞
            //4.1 数据库点赞量-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                //4.2 将用户从redis中移除
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void getBlogUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
