package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.auth.vo.LoginLogVO;
import com.example.pvplatform.persistence.entity.SysLoginLogDO;
import com.example.pvplatform.persistence.mapper.SysLoginLogMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogService(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public void record(Long userId, String username, String loginType,
                       String loginIp, String userAgent,
                       String status, String message) {
        SysLoginLogDO log = new SysLoginLogDO();
        log.setUserId(userId);
        log.setUsername(username != null ? username : "");
        log.setLoginType(loginType);
        log.setLoginIp(loginIp);
        log.setUserAgent(userAgent != null && userAgent.length() <= 512
            ? userAgent : (userAgent != null ? userAgent.substring(0, 512) : null));
        log.setStatus(status);
        log.setMessage(message);
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    public PageResult<LoginLogVO> page(int pageNum, int pageSize,
                                       String username, String status, String loginType) {
        Page<SysLoginLogDO> page = new Page<>(pageNum, pageSize);
        var result = loginLogMapper.selectPage(page, username, status, loginType);

        List<LoginLogVO> records = result.getRecords().stream()
            .map(this::toVO)
            .toList();

        return new PageResult<>(result.getTotal(), pageNum, pageSize, records);
    }

    private LoginLogVO toVO(SysLoginLogDO log) {
        return new LoginLogVO(
            log.getLogId(),
            log.getUserId(),
            log.getUsername(),
            log.getLoginType(),
            log.getLoginIp(),
            log.getUserAgent(),
            log.getStatus(),
            log.getMessage(),
            log.getLoginTime() != null
                ? log.getLoginTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null
        );
    }
}
