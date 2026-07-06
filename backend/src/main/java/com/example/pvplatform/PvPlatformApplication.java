package com.example.pvplatform;

import com.example.pvplatform.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class PvPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PvPlatformApplication.class, args);
    }
}
