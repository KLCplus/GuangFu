package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthServiceTest {
    @Autowired
    private AuthService authService;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // MockMvc sets up the request context needed for HttpServletRequest injection
    }

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = new RegisterRequest("testuser", "Test@1234", "test@example.com");
        Map<String, Object> result = authService.register(req);

        assertEquals("testuser", result.get("username"));
        assertNotNull(result.get("userId"));

        SysUserDO user = userMapper.selectById((Long) result.get("userId"));
        assertTrue(passwordEncoder.matches("Test@1234", user.getPasswordHash()));
        assertFalse(Boolean.TRUE.equals(user.getEmailVerified()));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        RegisterRequest req = new RegisterRequest("duplicate", "Test@1234", null);
        authService.register(req);

        assertThrows(BusinessException.class, () ->
            authService.register(new RegisterRequest("duplicate", "Test@5678", null)));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        authService.register(new RegisterRequest("user1", "Test@1234", "same@example.com"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            authService.register(new RegisterRequest("user2", "Test@5678", "same@example.com")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldLoginSuccessfully() {
        RegisterRequest regReq = new RegisterRequest("loginuser", "Test@1234", null);
        authService.register(regReq);

        LoginVO vo = authService.login(new LoginRequest("loginuser", "Test@1234"));

        assertNotNull(vo.token());
        assertFalse(vo.token().contains("mock-jwt-token"));
        assertEquals("loginuser", vo.userInfo().get("username"));
        assertTrue(vo.expiresIn() > 0);
        assertNotNull(vo.userInfo().get("roles"));
    }

    @Test
    void shouldRejectWrongPassword() {
        RegisterRequest regReq = new RegisterRequest("pwuser", "Test@1234", null);
        authService.register(regReq);

        BusinessException ex = assertThrows(BusinessException.class, () ->
            authService.login(new LoginRequest("pwuser", "Wrong@1234")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void shouldRejectDisabledUser() {
        RegisterRequest regReq = new RegisterRequest("disuser", "Test@1234", null);
        Map<String, Object> result = authService.register(regReq);

        SysUserDO user = userMapper.selectById((Long) result.get("userId"));
        user.setStatus(0);
        userMapper.updateById(user);

        BusinessException ex = assertThrows(BusinessException.class, () ->
            authService.login(new LoginRequest("disuser", "Test@1234")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void shouldUpdateLastLoginTime() {
        RegisterRequest regReq = new RegisterRequest("lastlogin2", "Test@1234", null);
        Map<String, Object> result = authService.register(regReq);
        Long userId = (Long) result.get("userId");

        authService.login(new LoginRequest("lastlogin2", "Test@1234"));

        SysUserDO user = userMapper.selectById(userId);
        assertNotNull(user.getLastLoginTime());
    }
}
