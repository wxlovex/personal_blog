package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.SubscribedAuthor;
import com.example.personal_blog.dto.SubscriptionStatus;
import com.example.personal_blog.service.SubscriptionService;
import com.example.personal_blog.utils.UserContext;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    @Override
    public SubscriptionStatus change(Long authorId, boolean subscribed) { throw retired(); }

    @Override
    public SubscriptionStatus status(Long authorId) { throw retired(); }

    @Override
    public Page<SubscribedAuthor> list(int page, int size) { throw retired(); }

    private BusinessException retired() {
        UserContext.requireUserId();
        return new BusinessException(ResultCode.GONE, "私人日记不提供作者订阅功能");
    }
}
