package com.example.pvplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    OpenAPI pvPlatformOpenApi() {
        return new OpenAPI().info(new Info()
            .title("光伏发电综合分析与预测系统 API")
            .version("0.1.0")
            .description("项目骨架版本，当前接口返回 mock 数据"));
    }
}
