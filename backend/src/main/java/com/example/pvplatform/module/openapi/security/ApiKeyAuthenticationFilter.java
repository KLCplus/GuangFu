package com.example.pvplatform.module.openapi.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.Result;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.service.ApiQuotaService;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ApiKeyService apiKeyService;
    private final ApiQuotaService quotaService;
    private final ApiCallLogService callLogService;
    private final ObjectMapper objectMapper;
    private final SysUserMapper userMapper;

    @Value("${security.debug-open:false}")
    private boolean debugOpen;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ApiQuotaService quotaService,
                                      ApiCallLogService callLogService, ObjectMapper objectMapper,
                                      SysUserMapper userMapper) {
        this.apiKeyService = apiKeyService;
        this.quotaService = quotaService;
        this.callLogService = callLogService;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/openapi/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader("X-API-KEY");
        // Debug mode only supplies a fallback identity when no key is provided. Explicit keys
        // still exercise real authentication so last-used, quota and billing stay observable.
        if (debugOpen && (rawKey == null || rawKey.isBlank())) {
            SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getStatus, 1)
                .orderByAsc(SysUserDO::getUserId)
                .last("LIMIT 1"));
            Long userId = user != null ? user.getUserId() : 1L;
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new ApiKeyPrincipal(null, userId), null, List.of()));
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
            return;
        }

        LocalDateTime started = LocalDateTime.now();
        ApiKeyDO key = null;
        try {
            if (rawKey == null || rawKey.isBlank()) {
                throw new BusinessException(401, "缺少 API Key");
            }
            key = apiKeyService.authenticate(rawKey);
            quotaService.checkAndConsume(key);
            ApiKeyPrincipal principal = new ApiKeyPrincipal(key.getApiKeyId(), key.getUserId());
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            filterChain.doFilter(request, response);
            if (request.getAttribute("OPEN_API_AUDITED") == null) {
                int status = response.getStatus();
                callLogService.save(key.getUserId(), key.getApiKeyId(), null,
                    request.getRequestURI(), request.getMethod(), clientIp(request), started,
                    status, status >= 400 ? "请求参数不合法" : null,
                    "{\"authenticated\":true}", null);
            }
        } catch (BusinessException e) {
            int status = e.getCode() >= 400 && e.getCode() <= 599 ? e.getCode() : 401;
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.fail(e.getCode(), e.getMessage()));
            callLogService.save(key == null ? null : key.getUserId(),
                key == null ? null : key.getApiKeyId(), null, request.getRequestURI(),
                request.getMethod(), clientIp(request), started, status, e.getMessage(),
                "{\"authenticated\":false}", null);
        } catch (Exception e) {
            response.setStatus(500);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.fail(500, "系统内部错误"));
            callLogService.save(key == null ? null : key.getUserId(),
                key == null ? null : key.getApiKeyId(), null, request.getRequestURI(),
                request.getMethod(), clientIp(request), started, 500, "系统内部错误",
                "{\"authenticated\":" + (key != null) + "}", null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
            : forwarded.split(",")[0].trim();
    }
}
