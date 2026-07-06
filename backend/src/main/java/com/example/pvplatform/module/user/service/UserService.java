package com.example.pvplatform.module.user.service;

import com.example.pvplatform.module.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public User profile() {
        return new User(1L, "demo", "demo@example.com", "USER", "ENABLE");
    }
}
