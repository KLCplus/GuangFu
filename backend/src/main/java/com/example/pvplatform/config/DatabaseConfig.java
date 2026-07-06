package com.example.pvplatform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.pvplatform.persistence.mapper")
public class DatabaseConfig {
}
