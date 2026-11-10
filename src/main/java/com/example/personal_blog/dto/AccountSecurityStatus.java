package com.example.personal_blog.dto;

public record AccountSecurityStatus(boolean mailEnabled, boolean emailVerified, String verifiedEmail,
                                    int activeSessions, StorageUsage storage, long maxStorageBytes,
                                    int maxArticles, int maxComments, int maxCategories,
                                    boolean verifiedEmailRequiredForPublication) {}
