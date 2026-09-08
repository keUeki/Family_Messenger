package com.shanyangcode.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 统一配置
 * <p>
 * 说明：此配置类位于 Common 模块，通过各服务的 scanBasePackages 被自动扫描加载
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件配置
     * <p>
     * 未注册该拦截器时，selectPage 不会生成 LIMIT 子句，
     * 会返回全表数据且 total 恒为 0。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件（指定数据库类型为 MySQL）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数，-1 表示不限制
        paginationInterceptor.setMaxLimit(500L);
        // 页码溢出总页数时不做处理（返回空列表而非回到首页）
        paginationInterceptor.setOverflow(false);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
