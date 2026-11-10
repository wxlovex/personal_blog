package com.example.personal_blog.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "blog.security")
public class SecurityProperties {
    @Min(1024) @Max(10485760)
    private int maxRequestBytes = 1048576;
    @Min(1) private int loginPerMinute = 20;
    @Min(1) private int accountLoginPerMinute = 5;
    @Min(1) private int registrationPerHour = 5;
    @Min(1) private int writesPerMinute = 60;
    @Min(1) private int readsPerMinute = 240;
    @Min(100) private int limiterMaxKeys = 20000;
    private boolean registrationEnabled = true;
    @Min(1) private int sensitivePerMinute = 5;
    @Min(1) private int emailPerHour = 3;
    @Min(1) private long maxStorageBytes = 20971520;
    @Min(1) private int maxArticles = 1000;
    @Min(1) private int maxComments = 10000;
    @Min(1) private int maxCategories = 200;
    private boolean requireVerifiedEmailForPublication = false;
}
