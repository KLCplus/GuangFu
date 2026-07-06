package com.example.pvplatform.module.auth.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.auth.dto.EmailCodeLoginRequest;
import com.example.pvplatform.module.auth.dto.EmailCodeRegisterRequest;
import com.example.pvplatform.module.auth.dto.ForgotPasswordRequest;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.OAuthCallbackRequest;
import com.example.pvplatform.module.auth.dto.RefreshTokenRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.dto.ResetPasswordRequest;
import com.example.pvplatform.module.auth.dto.SendCodeRequest;
import com.example.pvplatform.module.auth.service.AuthService;
import com.example.pvplatform.module.auth.service.FaceAuthService;
import com.example.pvplatform.module.auth.service.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final OAuthService oauthService;
    private final FaceAuthService faceAuthService;

    public AuthController(AuthService authService, OAuthService oauthService,
                          FaceAuthService faceAuthService) {
        this.authService = authService;
        this.oauthService = oauthService;
        this.faceAuthService = faceAuthService;
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        authService.logout();
        return Result.success();
    }

    @PostMapping("/forgot-password")
    public Result<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return Result.success();
    }

    // ---- Email code ----

    @PostMapping("/email/code/send")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.email());
        return Result.success();
    }

    @PostMapping("/email/code/login")
    public Result<?> emailCodeLogin(@Valid @RequestBody EmailCodeLoginRequest request) {
        return Result.success(authService.emailCodeLogin(request.email(), request.code()));
    }

    @PostMapping("/email/code/register")
    public Result<?> emailCodeRegister(@Valid @RequestBody EmailCodeRegisterRequest request) {
        return Result.success(authService.emailCodeRegister(
            request.username(), request.email(), request.code()));
    }

    // ---- OAuth ----

    @GetMapping("/oauth/{provider}/authorize")
    public Result<?> oauthAuthorize(@PathVariable String provider,
                                     @RequestParam String redirectUri) {
        return Result.success(oauthService.authorize(provider, redirectUri));
    }

    @GetMapping("/oauth/{provider}/callback")
    public void oauthGetCallback(@PathVariable String provider,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state,
                                  HttpServletResponse response) throws IOException {
        if (code == null || state == null) {
            // 这个地址是给 GitHub 回调用的，不是手动打开的
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>"
                + "<h3>这是 GitHub OAuth 回调地址</h3>"
                + "<p>请从 <a href='/login.html'>登录页</a> 点击「GitHub 登录」按钮发起授权，不要直接访问此地址。</p>"
                + "</body></html>");
            return;
        }
        try {
            String redirectUrl = oauthService.handleGetCallback(provider, code, state);
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            response.sendRedirect("/login.html?error=" + java.net.URLEncoder.encode(
                "OAuth 登录处理失败，请重新尝试", "UTF-8"));
        }
    }

    @PostMapping("/oauth/{provider}/callback")
    public Result<?> oauthCallback(@PathVariable String provider,
                                    @Valid @RequestBody OAuthCallbackRequest request) {
        return Result.success(oauthService.callback(provider, request.code(), request.state(),
            request.redirectUri() != null ? request.redirectUri() : ""));
    }

    // ---- Face recognition ----

    @PostMapping("/face-login")
    public Result<?> faceLogin(@RequestParam("file") MultipartFile file) {
        return Result.success(faceAuthService.login(file));
    }
}
