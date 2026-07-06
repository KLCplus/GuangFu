package com.example.pvplatform.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   SysUserMapper userMapper,
                                   SysRoleMapper roleMapper,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            TokenClaims claims = jwtTokenService.parseToken(token);

            SysUserDO user = userMapper.selectById(claims.userId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                authenticationEntryPoint.commence(request, response,
                    new SecurityException("用户不存在或已被禁用"));
                return;
            }

            // Check token version: tokens issued before a password change / logout are invalid
            Integer userVersion = user.getTokenVersion();
            if (userVersion != null && claims.tokenVersion() != null
                && !userVersion.equals(claims.tokenVersion())) {
                authenticationEntryPoint.commence(request, response,
                    new SecurityException("登录状态已变更，请重新登录"));
                return;
            }

            List<String> roles = roleMapper.selectRoleCodesByUserId(claims.userId());

            SecurityUser securityUser = new SecurityUser(
                user.getUserId(), user.getUsername(), user.getStatus(), roles);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            authenticationEntryPoint.commence(request, response,
                new SecurityException("登录状态无效或已过期"));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
