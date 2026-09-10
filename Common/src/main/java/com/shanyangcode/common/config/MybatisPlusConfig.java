package com.shanyangcode.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared MyBatis-Plus configuration
 * <p>
 * Note: this class lives in the Common module and is picked up automatically via each service's scanBasePackages.
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * Pagination plugin configuration
     * <p>
     * Without this interceptor registered, selectPage does not emit a LIMIT clause,
     * so it returns the whole table and total is always 0.
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // Pagination plugin (database type set to MySQL)
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // Maximum rows per page; -1 means unlimited
        paginationInterceptor.setMaxLimit(500L);
        // Do not clamp when the page number exceeds the total (return an empty list instead of the first page)
        paginationInterceptor.setOverflow(false);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
