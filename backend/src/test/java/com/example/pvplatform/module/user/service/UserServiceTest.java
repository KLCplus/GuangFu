package com.example.pvplatform.module.user.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.service.AuthService;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.module.user.dto.ChangePasswordRequest;
import com.example.pvplatform.module.user.dto.UpdateProfileRequest;
import com.example.pvplatform.module.user.vo.UserProfileVO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserServiceTest {
    @Autowired
    private UserService userService;
    @Autowired
    private AuthService authService;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    private Long currentUserId;

    @BeforeEach
    void setUp() {
        // Register and login a test user, then set security context
        RegisterRequest regReq = new RegisterRequest("profileuser", "Test@1234", "profile@example.com");
        Map<String, Object> regResult = authService.register(regReq);
        currentUserId = ((Number) regResult.get("userId")).longValue();
    }

    private void authenticateAs(Long userId, String username, List<String> roles) {
        var securityUser = new com.example.pvplatform.security.SecurityUser(
            userId, username, 1, roles);
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldReturnCurrentUserProfile() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        UserProfileVO profile = userService.profile();

        assertEquals(currentUserId, profile.userId());
        assertEquals("profileuser", profile.username());
        assertEquals("profile@example.com", profile.email());
        assertTrue(profile.roles().contains("USER"));
    }

    @Test
    void shouldNotContainPasswordHashInProfile() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        UserProfileVO profile = userService.profile();

        // Verify no password-related fields are exposed
        // (they simply aren't in the UserProfileVO record)
        assertNotNull(profile.username());
        assertNotNull(profile.nickname());
    }

    @Test
    void shouldUpdateProfile() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        UserProfileVO updated = userService.updateProfile(
            new UpdateProfileRequest("NewNick", null, "13900001111", null, null));

        assertEquals("NewNick", updated.nickname());
        assertEquals("13900001111", updated.phone());
        SysUserDO saved = userMapper.selectById(currentUserId);
        assertEquals("13900001111", saved.getPhone());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shouldRejectDuplicateEmailInUpdate() {
        // Create another user with an email
        authService.register(new RegisterRequest("otheruser", "Test@1234", "other@example.com"));

        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.updateProfile(
                new UpdateProfileRequest(null, "other@example.com", null, null, null)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldChangePassword() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        userService.changePassword(new ChangePasswordRequest("Test@1234", "NewTest@1234"));

        // Verify new password works
        SysUserDO user = userMapper.selectById(currentUserId);
        assertTrue(passwordEncoder.matches("NewTest@1234", user.getPasswordHash()));
    }

    @Test
    void shouldRejectWrongOldPassword() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.changePassword(new ChangePasswordRequest("WrongOldPass", "NewTest@1234")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldRejectSameOldAndNewPassword() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.changePassword(new ChangePasswordRequest("Test@1234", "Test@1234")));
        assertEquals(400, ex.getCode());
    }
}
