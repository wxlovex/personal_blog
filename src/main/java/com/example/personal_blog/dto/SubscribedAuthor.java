package com.example.personal_blog.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SubscribedAuthor {
    private Long id;
    private String username;
    private LocalDateTime subscribedAt;
    private Long articleCount;
}
