package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.SubscribedAuthor;
import com.example.personal_blog.dto.SubscriptionStatus;
import com.example.personal_blog.entity.UserSubscription;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.mapper.UserSubscriptionMapper;
import com.example.personal_blog.service.SubscriptionService;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final UserSubscriptionMapper subscriptionMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubscriptionStatus change(Long authorId, boolean subscribed) {
        Long subscriberId = UserContext.requireUserId();
        if (subscriberId.equals(authorId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能订阅自己");
        }
        lockParticipants(subscriberId, authorId);
        if (subscribed) {
            subscriptionMapper.subscribe(subscriberId, authorId, LocalDateTime.now(ZoneOffset.UTC));
        } else {
            subscriptionMapper.unsubscribe(subscriberId, authorId);
        }
        return new SubscriptionStatus(authorId, subscribed, subscriberCount(authorId));
    }

    @Override
    public SubscriptionStatus status(Long authorId) {
        requireAuthor(authorId);
        Long viewerId = UserContext.getUserId();
        boolean subscribed = viewerId != null && subscriptionMapper.selectCount(
                new QueryWrapper<UserSubscription>().eq("subscriber_id", viewerId).eq("author_id", authorId)) > 0;
        return new SubscriptionStatus(authorId, subscribed, subscriberCount(authorId));
    }

    @Override
    public Page<SubscribedAuthor> list(int page, int size) {
        return subscriptionMapper.listAuthors(new Page<>(page, size), requireExistingViewer());
    }

    private Long requireExistingViewer() {
        Long id = UserContext.requireUserId();
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return id;
    }

    private void lockParticipants(Long subscriberId, Long authorId) {
        // 先按固定顺序锁住双方，再修改关系。只锁一方会让双向订阅的外键检查形成环路。
        // 行锁在事务结束时释放，同一关系的订阅/取消跨应用实例也会串行执行。
        long firstId = Math.min(subscriberId, authorId);
        long secondId = Math.max(subscriberId, authorId);
        Long first = userMapper.lockId(firstId);
        Long second = userMapper.lockId(secondId);
        if ((subscriberId == firstId ? first : second) == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        if ((authorId == firstId ? first : second) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作者不存在");
        }
    }

    private void requireAuthor(Long authorId) {
        if (userMapper.selectById(authorId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作者不存在");
        }
    }

    private long subscriberCount(Long authorId) {
        return subscriptionMapper.selectCount(new QueryWrapper<UserSubscription>().eq("author_id", authorId));
    }
}
