package com.example.pvplatform.module.user.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.user.dto.CancelAccountRequest;
import com.example.pvplatform.module.user.dto.ChangePasswordRequest;
import com.example.pvplatform.module.user.dto.UpdateProfileRequest;
import com.example.pvplatform.module.auth.dto.OAuthCallbackRequest;
import com.example.pvplatform.module.auth.service.FaceAuthService;
import com.example.pvplatform.module.auth.service.OAuthService;
import com.example.pvplatform.module.user.service.AvatarService;
import com.example.pvplatform.module.user.service.UserService;
import com.example.pvplatform.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
public class UserController {
    private final UserService userService;
    private final AvatarService avatarService;
    private final OAuthService oauthService;
    private final FaceAuthService faceAuthService;

    public UserController(UserService userService, AvatarService avatarService,
                          OAuthService oauthService, FaceAuthService faceAuthService) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.oauthService = oauthService;
        this.faceAuthService = faceAuthService;
    }

    @GetMapping("/api/user/profile")
    public Result<?> profile() {
        return Result.success(userService.profile());
    }

    @PutMapping("/api/user/profile")
    public Result<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(request));
    }

    @PutMapping("/api/user/password")
    public Result<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.success();
    }

    @PostMapping("/api/user/account/cancel")
    public Result<?> cancelAccount(@Valid @RequestBody CancelAccountRequest request) {
        userService.cancelAccount(request);
        return Result.success();
    }

    @PostMapping("/api/user/avatar")
    public Result<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.requireCurrentUserId();
        String url = avatarService.upload(file, userId);

        // Update user's avatar_url
        userService.updateAvatarUrl(url);

        return Result.success(Map.of("avatarUrl", url));
    }

    @GetMapping("/api/avatars/{storageName}")
    public ResponseEntity<byte[]> serveAvatar(@PathVariable String storageName) {
        byte[] data = avatarService.serve(storageName);
        String contentType = guessContentType(storageName);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(data);
    }

    private String guessContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ---- OAuth account management ----

    @GetMapping("/api/user/oauth-accounts")
    public Result<?> listOAuthAccounts() {
        return Result.success(oauthService.listMyOAuthAccounts());
    }

    @PostMapping("/api/user/oauth-accounts/{provider}/bind")
    public Result<?> bindOAuthAccount(@PathVariable String provider,
                                       @Valid @RequestBody OAuthCallbackRequest request) {
        return Result.success(oauthService.bind(provider, request.code(), request.state(),
            request.redirectUri() != null ? request.redirectUri() : ""));
    }

    @DeleteMapping("/api/user/oauth-accounts/{oauthId}")
    public Result<?> unbindOAuthAccount(@PathVariable Long oauthId) {
        oauthService.unbind(oauthId);
        return Result.success();
    }

    // ---- Face recognition ----

    @PostMapping("/api/user/face/enroll")
    public Result<?> enrollFace(@RequestParam("file") MultipartFile file) {
        faceAuthService.enroll(file);
        return Result.success();
    }

    @DeleteMapping("/api/user/face")
    public Result<?> revokeFace() {
        faceAuthService.revoke();
        return Result.success();
    }

    @GetMapping("/api/user/face")
    public Result<?> faceStatus() {
        return Result.success(faceAuthService.status());
    }
}
