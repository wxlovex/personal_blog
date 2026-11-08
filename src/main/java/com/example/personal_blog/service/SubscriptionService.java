package com.example.personal_blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.dto.SubscribedAuthor;
import com.example.personal_blog.dto.SubscriptionStatus;

public interface SubscriptionService {
    SubscriptionStatus change(Long authorId, boolean subscribed);
    SubscriptionStatus status(Long authorId);
    Page<SubscribedAuthor> list(int page, int size);
}
