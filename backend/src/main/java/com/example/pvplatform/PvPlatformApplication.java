package com.example.pvplatform;

import com.example.pvplatform.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableCaching
@EnableAsync
public class PvPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PvPlatformApplication.class, args);
    }

}
