package com.example.pvplatform.security;

import java.util.List;

public record TokenClaims(Long userId, String username, List<String> roles,
                         Integer tokenVersion, String type) {
}
