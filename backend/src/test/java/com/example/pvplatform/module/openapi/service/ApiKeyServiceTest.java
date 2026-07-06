package com.example.pvplatform.module.openapi.service;

import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.mapper.ApiKeyMapper;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {
    private ApiKeyMapper mapper;
    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApiKeyMapper.class);
        service = new ApiKeyService(mapper);
        SecurityUser user = new SecurityUser(7L, "api-user", 1, List.of("API_USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsUniqueOneTimeKeysAndStoresOnlyHash() {
        AtomicReference<ApiKeyDO> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            ApiKeyDO row = invocation.getArgument(0);
            row.setApiKeyId(1L);
            stored.set(row);
            return 1;
        }).when(mapper).insert(any(ApiKeyDO.class));

        var first = service.create(new ApiKeyApplyRequest("integration", 30));
        var second = service.create(new ApiKeyApplyRequest("integration", 30));

        assertNotEquals(first.apiKey(), second.apiKey());
        assertTrue(first.apiKey().matches("^pv_[0-9a-f]{8}_[A-Za-z0-9_-]{43}$"));
        assertNotEquals(first.apiKey(), stored.get().getApiKeyHash());
        assertEquals(64, stored.get().getApiKeyHash().length());
        assertFalse(stored.get().getApiKeyHash().contains("pv_"));
    }
}
