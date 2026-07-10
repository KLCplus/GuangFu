package com.example.pvplatform;

import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.example.pvplatform.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, LlmProperties.class})
@EnableCaching
@EnableAsync
@EnableScheduling
public class PvPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PvPlatformApplication.class, args);
    }

}
