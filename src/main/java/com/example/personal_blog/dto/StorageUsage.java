package com.example.personal_blog.dto;

import lombok.Data;

@Data
public class StorageUsage {
    private long usedBytes;
    private long articles;
    private long comments;
    private long categories;
}
