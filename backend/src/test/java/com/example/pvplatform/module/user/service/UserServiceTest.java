package com.example.pvplatform.module.user.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.common.EmailService;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.service.AuthService;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.module.user.dto.ChangePasswordRequest;
import com.example.pvplatform.module.user.dto.ConfirmEmailChangeRequest;
import com.example.pvplatform.module.user.dto.SendEmailChangeCodeRequest;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

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
    @Autowired
    private com.example.pvplatform.module.auth.service.VerificationCodeService codeService;
    @MockBean
    private EmailService emailService;

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
            new UpdateProfileRequest("NewNick", "13900001111", null, null));

        assertEquals("NewNick", updated.nickname());
        assertEquals("+8613900001111", updated.phone());
        SysUserDO saved = userMapper.selectById(currentUserId);
        assertEquals("+8613900001111", saved.getPhone());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shouldRejectInvalidContactPhone() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));
        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.updateProfile(
                new UpdateProfileRequest(null, "not-a-phone", null, null)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void profileEndpointMustIgnoreDirectEmailUpdate() throws Exception {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"attacker@example.com\",\"nickname\":\"SafeNick\"}"))
            .andExpect(status().isOk());

        SysUserDO saved = userMapper.selectById(currentUserId);
        assertEquals("profile@example.com", saved.getEmail());
        assertEquals("SafeNick", saved.getNickname());
    }

    @Test
    void shouldChangeEmailOnlyAfterPasswordAndCodeVerification() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));
        String newEmail = "verified-new@example.com";
        String key = "email-change:" + currentUserId + ":" + newEmail;
        codeService.clear(key);

        userService.sendEmailChangeCode(
            new SendEmailChangeCodeRequest("  VERIFIED-NEW@example.com ", "Test@1234"));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(newEmail), eq("光伏平台 - 更换邮箱验证码"), bodyCaptor.capture());
        java.util.regex.Matcher codeMatcher = java.util.regex.Pattern.compile("\\b(\\d{6})\\b")
            .matcher(bodyCaptor.getValue());
        assertTrue(codeMatcher.find());
        String code = codeMatcher.group(1);

        assertEquals("profile@example.com", userMapper.selectById(currentUserId).getEmail());
        UserProfileVO changed = userService.confirmEmailChange(
            new ConfirmEmailChangeRequest("verified-new@EXAMPLE.com", " " + code + " ", "Test@1234"));

        assertEquals(newEmail, changed.email());
        assertTrue(changed.emailVerified());
        assertEquals(newEmail, userMapper.selectById(currentUserId).getEmail());

        BusinessException reused = assertThrows(BusinessException.class,
            () -> codeService.verify(key, code));
        assertEquals("验证码不存在或已过期", reused.getMessage());
    }

    @Test
    void wrongAndExpiredEmailChangeCodesMustNotChangeEmail() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));
        String newEmail = "unchanged@example.com";
        String key = "email-change:" + currentUserId + ":" + newEmail;
        codeService.clear(key);
        String validCode = codeService.generate(key);

        BusinessException wrong = assertThrows(BusinessException.class, () ->
            userService.confirmEmailChange(
                new ConfirmEmailChangeRequest(newEmail, "999999", "Test@1234")));
        assertEquals("验证码错误", wrong.getMessage());
        assertEquals("profile@example.com", userMapper.selectById(currentUserId).getEmail());

        codeService.clear(key);
        BusinessException expired = assertThrows(BusinessException.class, () ->
            userService.confirmEmailChange(
                new ConfirmEmailChangeRequest(newEmail, validCode, "Test@1234")));
        assertEquals("验证码不存在或已过期", expired.getMessage());
        assertEquals("profile@example.com", userMapper.selectById(currentUserId).getEmail());
    }

    @Test
    void shouldSendChangeCodeWithoutChangingCurrentEmail() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));
        String newEmail = "pending-new@example.com";

        userService.sendEmailChangeCode(new SendEmailChangeCodeRequest(newEmail, "Test@1234"));

        SysUserDO saved = userMapper.selectById(currentUserId);
        assertEquals("profile@example.com", saved.getEmail());
        assertFalse(Boolean.TRUE.equals(saved.getEmailVerified()));
        verify(emailService).send(eq(newEmail), eq("光伏平台 - 更换邮箱验证码"), anyString());
    }

    @Test
    void shouldReturnClearErrorWhenAccountHasNoLocalPassword() {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));
        SysUserDO user = userMapper.selectById(currentUserId);
        user.setPasswordHash("");
        userMapper.updateById(user);

        BusinessException exception = assertThrows(BusinessException.class, () ->
            userService.sendEmailChangeCode(
                new SendEmailChangeCodeRequest("pending-new@example.com", "irrelevant")));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("未设置本地密码"));
        verifyNoInteractions(emailService);
    }

    @Test
    void emailChangeEndpointShouldExposeValidationAndPasswordErrors() throws Exception {
        authenticateAs(currentUserId, "profileuser", List.of("USER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/user/security/email/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"bad-email\",\"currentPassword\":\"Test@1234\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("新邮箱格式不正确"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/user/security/email/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"pending-new@example.com\",\"currentPassword\":\"wrong\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("当前密码错误"));
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
