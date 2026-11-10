package com.example.personal_blog.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Public projection: never include private categories or supplementary notes. */
@Data
public class PlazaArticle {
    private Long id;
    private Long authorId;
    private String authorName;
    private String title;
    private String content;
    private String summary;
    private String category;
    private LocalDateTime publishedTime;
    private LocalDateTime updateTime;
}
