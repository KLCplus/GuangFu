package com.example.pvplatform.module.auth.vo;

import java.util.Map;

public record LoginVO(String token, Map<String, Object> userInfo) {}
