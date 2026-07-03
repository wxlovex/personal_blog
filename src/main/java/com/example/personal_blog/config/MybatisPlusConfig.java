package com.example.personal_blog.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        // 显式指定数据库类型，避免自动检测失败
        paginationInterceptor.setDbType(com.baomidou.mybatisplus.annotation.DbType.MYSQL);
        // 单页最大记录数，防止恶意传超大 pageSize 拖垮查询
        paginationInterceptor.setMaxLimit(500L);
        // 超出总页数时回到首页，而不是返回空列表
        paginationInterceptor.setOverflow(true);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
