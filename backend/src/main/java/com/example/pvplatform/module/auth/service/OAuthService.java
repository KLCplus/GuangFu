package com.example.pvplatform.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.oauth.*;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.module.auth.vo.OAuthAccountVO;
import com.example.pvplatform.module.auth.vo.OAuthAuthorizeVO;
import com.example.pvplatform.persistence.entity.SysOAuthAccountDO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.SysOAuthAccountMapper;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.JwtTokenService;
import com.example.pvplatform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class OAuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysOAuthAccountMapper oauthMapper;
    private final JwtTokenService jwtTokenService;
    private final LoginLogService logService;
    private final OAuthStateSigner stateSigner;
    private final List<OAuthProviderConfig> providers;
    private final WebClient.Builder webClientBuilder;
    private final HttpServletRequest request;

    public OAuthService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                        SysUserRoleMapper userRoleMapper, SysOAuthAccountMapper oauthMapper,
                        JwtTokenService jwtTokenService, LoginLogService logService,
                        OAuthStateSigner stateSigner,
                        WebClient.Builder webClientBuilder, HttpServletRequest request) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.oauthMapper = oauthMapper;
        this.jwtTokenService = jwtTokenService;
        this.logService = logService;
        this.stateSigner = stateSigner;
        this.webClientBuilder = webClientBuilder;
        this.request = request;
        this.providers = new ArrayList<>();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProviders(List<OAuthProviderConfig> providers) {
        if (providers != null) {
            this.providers.clear();
            this.providers.addAll(providers);
        }
    }

    @org.springframework.beans.factory.annotation.Value("${server.port:8080}")
    private int serverPort;

    @org.springframework.beans.factory.annotation.Value("${oauth.callback-base-url:http://localhost:5173}")
    private String callbackBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${oauth.backend-callback-base-url:}")
    private String backendCallbackBaseUrl;

    /**
     * Build the authorize URL. GitHub redirects back to backend callback, which then
     * redirects to the frontend URL (stored in signed state).
     */
    public OAuthAuthorizeVO authorize(String providerCode, String redirectUri) {
        OAuthProviderConfig provider = getProvider(providerCode);
        if (provider.isMock()) {
            String state = stateSigner.sign(providerCode, redirectUri);
            return new OAuthAuthorizeVO(
                redirectUri + "?provider=" + providerCode + "&state=" + state + "&code=mock_code",
                state);
        }
        String state = stateSigner.sign(providerCode, redirectUri);
        String backendCallback = backendCallbackUrl(providerCode);
        String url = provider.authorizeUrl()
            + "?client_id=" + urlEncode(provider.clientId())
            + "&redirect_uri=" + urlEncode(backendCallback)
            + "&response_type=code"
            + "&scope=" + urlEncode(provider.scopes() != null ? provider.scopes() : "")
            + "&state=" + urlEncode(state);
        return new OAuthAuthorizeVO(url, state);
    }

    /**
     * GET callback from GitHub. Processes OAuth, then redirects browser to frontend URL with JWT.
     */
    public String handleGetCallback(String providerCode, String code, String state) {
        try {
            OAuthProviderConfig provider = getProvider(providerCode);
            OAuthStateSigner.ParsedState parsed = stateSigner.verify(state, providerCode);
            String frontendUrl = parsed.redirectUri();

            String backendCallbackUrl = backendCallbackUrl(providerCode);

            LoginVO login;
            if (provider.isMock()) {
                login = callback(providerCode, code, state, frontendUrl);
            } else {
                login = callback(providerCode, code, state, backendCallbackUrl);
            }

            return frontendUrl
                + (frontendUrl.contains("?") ? "&" : "?")
                + "token=" + urlEncode(login.token())
                + "&refreshToken=" + urlEncode(login.refreshToken())
                + "&expiresIn=" + login.expiresIn();
        } catch (Exception e) {
            String frontendUrl = callbackBaseUrl + "/login";
            String message = e instanceof BusinessException
                ? e.getMessage()
                : "OAuth 登录处理失败，请重新尝试";
            return frontendUrl + "?error=" + urlEncode(message);
        }
    }

    /**
     * Handle callback with frontend-provided redirectUri.
     */
    @Transactional
    public LoginVO callback(String providerCode, String code, String state, String redirectUri) {
        OAuthProviderConfig provider = getProvider(providerCode);
        stateSigner.verify(state, providerCode);

        OAuthUserInfo userInfo;
        if (provider.isMock()) {
            userInfo = new OAuthUserInfo("mock-openid-" + code, null, "MockUser", null, null);
        } else {
            userInfo = exchangeAndFetch(provider, code, redirectUri);
        }

        SysOAuthAccountDO existing = oauthMapper.selectOne(
            Wrappers.<SysOAuthAccountDO>lambdaQuery()
                .eq(SysOAuthAccountDO::getProvider, providerCode)
                .eq(SysOAuthAccountDO::getOpenId, userInfo.openId())
                .last("LIMIT 1"));

        if (existing != null) {
            // Update nickname/avatar/email on re-login
            existing.setNickname(userInfo.nickname());
            existing.setAvatarUrl(userInfo.avatarUrl());
            if (userInfo.email() != null) existing.setEmail(userInfo.email());
            oauthMapper.updateById(existing);

            SysUserDO user = userMapper.selectById(existing.getUserId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(401, "绑定的用户不存在或已禁用");
            }
            return issueOAuthLogin(user, providerCode, "");
        }

        // If GitHub returns an email already owned by a local account, bind the
        // OAuth identity to that account instead of violating uk_user_email.
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            SysUserDO localUser = userMapper.selectOne(
                Wrappers.<SysUserDO>lambdaQuery()
                    .eq(SysUserDO::getEmail, userInfo.email().trim())
                    .last("LIMIT 1"));
            if (localUser != null) {
                if (localUser.getStatus() == null || localUser.getStatus() != 1) {
                    throw new BusinessException(401, "该邮箱对应的用户已被禁用");
                }
                createOAuthAccount(localUser.getUserId(), providerCode, userInfo);
                return issueOAuthLogin(localUser, providerCode, " (linked by email)");
            }
        }

        return createUserAndLogin(providerCode, userInfo);
    }

    @Transactional
    public OAuthAccountVO bind(String providerCode, String code, String state, String redirectUri) {
        Long userId = SecurityUtils.requireCurrentUserId();
        OAuthProviderConfig provider = getProvider(providerCode);
        stateSigner.verify(state, providerCode);

        OAuthUserInfo userInfo;
        if (provider.isMock()) {
            userInfo = new OAuthUserInfo("mock-openid-" + code, null, "MockUser", null, null);
        } else {
            userInfo = exchangeAndFetch(provider, code, redirectUri);
        }

        Long count = oauthMapper.selectCount(
            Wrappers.<SysOAuthAccountDO>lambdaQuery()
                .eq(SysOAuthAccountDO::getProvider, providerCode)
                .eq(SysOAuthAccountDO::getOpenId, userInfo.openId()));
        if (count > 0) {
            throw new BusinessException(400, "该第三方账号已绑定其他用户");
        }

        SysOAuthAccountDO account = new SysOAuthAccountDO();
        account.setUserId(userId);
        account.setProvider(providerCode);
        account.setOpenId(userInfo.openId());
        account.setUnionId(userInfo.unionId());
        account.setNickname(userInfo.nickname());
        account.setAvatarUrl(userInfo.avatarUrl());
        account.setEmail(userInfo.email());
        oauthMapper.insert(account);

        return new OAuthAccountVO(account.getOauthId(), account.getProvider(),
            maskOpenId(account.getOpenId()), account.getNickname(), account.getAvatarUrl(),
            account.getEmail(), account.getCreatedAt() != null ? account.getCreatedAt().toString() : null);
    }

    @Transactional
    public void unbind(Long oauthId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysOAuthAccountDO account = oauthMapper.selectById(oauthId);
        if (account == null || !account.getUserId().equals(userId)) {
            throw new BusinessException(404, "OAuth 绑定不存在");
        }
        oauthMapper.deleteById(oauthId);
    }

    public List<OAuthAccountVO> listMyOAuthAccounts() {
        Long userId = SecurityUtils.requireCurrentUserId();
        List<SysOAuthAccountDO> accounts = oauthMapper.selectList(
            Wrappers.<SysOAuthAccountDO>lambdaQuery()
                .eq(SysOAuthAccountDO::getUserId, userId));
        return accounts.stream().map(a -> new OAuthAccountVO(
            a.getOauthId(), a.getProvider(), maskOpenId(a.getOpenId()),
            a.getNickname(), a.getAvatarUrl(), a.getEmail(),
            a.getCreatedAt() != null ? a.getCreatedAt().toString() : null
        )).toList();
    }

    // ---- private helpers ----

    private OAuthProviderConfig getProvider(String code) {
        OAuthProviderConfig provider = providers.stream()
            .filter(p -> p.code().equalsIgnoreCase(code) && p.enabled())
            .findFirst().orElse(null);
        if (provider == null) {
            throw new BusinessException(400, "不支持的 OAuth 提供商: " + code);
        }
        return provider;
    }

    private OAuthUserInfo exchangeAndFetch(OAuthProviderConfig provider, String code, String redirectUri) {
        try {
            MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
            tokenForm.add("client_id", provider.clientId());
            tokenForm.add("client_secret", provider.clientSecret());
            tokenForm.add("code", code);
            tokenForm.add("redirect_uri", redirectUri);
            tokenForm.add("grant_type", "authorization_code");

            Map<String, Object> tokenResp = webClientBuilder.build().post()
                .uri(provider.tokenUrl())
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(tokenForm)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (tokenResp == null || !tokenResp.containsKey("access_token")) {
                throw new BusinessException(502, "OAuth 令牌交换失败");
            }
            String accessToken = (String) tokenResp.get("access_token");

            @SuppressWarnings("unchecked")
            Map<String, Object> userResp = webClientBuilder.build().get()
                .uri(provider.userInfoUrl())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (userResp == null) {
                throw new BusinessException(502, "OAuth 用户信息获取失败");
            }

            String openId = Objects.toString(userResp.get("id"), "");
            String unionId = Objects.toString(userResp.get("node_id"), null);
            String nickname = Objects.toString(userResp.get("login"),
                Objects.toString(userResp.get("name"), null));
            String avatar = Objects.toString(userResp.get("avatar_url"), null);
            String email = Objects.toString(userResp.get("email"), null);

            return new OAuthUserInfo(openId, unionId, nickname, avatar, email);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "OAuth 认证服务异常");
        }
    }

    @Transactional
    private LoginVO createUserAndLogin(String providerCode, OAuthUserInfo userInfo) {
        String username = "oauth_" + providerCode + "_" + Math.abs(userInfo.openId().hashCode() % 100000);
        if (username.length() > 32) username = username.substring(0, 32);

        SysUserDO user = new SysUserDO();
        user.setUsername(username);
        user.setPasswordHash("");
        user.setNickname(userInfo.nickname() != null ? userInfo.nickname() : username);
        user.setAvatarUrl(userInfo.avatarUrl());
        user.setEmail(userInfo.email());
        user.setEmailVerified(false);
        user.setStatus(1);
        user.setTokenVersion(0);
        userMapper.insert(user);

        SysRoleDO userRole = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, "USER").eq(SysRoleDO::getStatus, 1).last("LIMIT 1"));
        if (userRole != null) {
            SysUserRoleDO ur = new SysUserRoleDO();
            ur.setUserId(user.getUserId());
            ur.setRoleId(userRole.getRoleId());
            userRoleMapper.insert(ur);
        }

        createOAuthAccount(user.getUserId(), providerCode, userInfo);
        return issueOAuthLogin(user, providerCode, " (new user)");
    }

    private void createOAuthAccount(Long userId, String providerCode, OAuthUserInfo userInfo) {
        SysOAuthAccountDO oauth = new SysOAuthAccountDO();
        oauth.setUserId(userId);
        oauth.setProvider(providerCode);
        oauth.setOpenId(userInfo.openId());
        oauth.setUnionId(userInfo.unionId());
        oauth.setNickname(userInfo.nickname());
        oauth.setAvatarUrl(userInfo.avatarUrl());
        oauth.setEmail(userInfo.email());
        oauthMapper.insert(oauth);
    }

    private LoginVO issueOAuthLogin(SysUserDO user, String providerCode, String logSuffix) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        String accessToken = jwtTokenService.generateAccessToken(
            user.getUserId(), user.getUsername(), roles, version);
        String refreshToken = jwtTokenService.generateRefreshToken(user.getUserId(), version);

        logService.record(user.getUserId(), user.getUsername(), "OAUTH_LOGIN",
            getClientIp(), getUserAgent(), "SUCCESS",
            "provider=" + providerCode + logSuffix);

        return LoginVO.of(accessToken, jwtTokenService.getAccessExpirationSeconds(),
            refreshToken, jwtTokenService.getRefreshExpirationSeconds(),
            user.getUserId(), user.getUsername(), user.getNickname(), roles);
    }

    private String urlEncode(String value) {
        if (value == null) return "";
        try { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { return value; }
    }

    /** Resolves the public callback address for local development and deployments. */
    private String backendCallbackUrl(String providerCode) {
        String base = backendCallbackBaseUrl;
        if (base == null || base.isBlank()) {
            String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
            if (forwardedHost != null && !forwardedHost.isBlank()) {
                String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
                if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
                String prefix = firstForwardedValue(request.getHeader("X-Forwarded-Prefix"));
                base = scheme + "://" + forwardedHost + (prefix == null ? "" : prefix);
            } else {
                base = "http://localhost:" + serverPort;
            }
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/api/auth/oauth/" + providerCode + "/callback";
    }

    private String firstForwardedValue(String value) {
        if (value == null) return null;
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip))
            ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }

    private String getUserAgent() { return request.getHeader("User-Agent"); }

    private String maskOpenId(String o) {
        return o != null && o.length() > 6 ? o.substring(0, 3) + "****" + o.substring(o.length() - 3) : o;
    }
}
