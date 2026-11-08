package com.example.personal_blog.dto;

public record SubscriptionStatus(Long authorId, boolean subscribed, long subscriberCount) {
}
