package com.example.pvplatform.security;

import com.example.pvplatform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401,
            authException.getMessage());
    }

    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         Exception exception) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401,
            exception.getMessage());
    }

    private void writeJson(HttpServletResponse response, int httpStatus,
                           int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
