package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLogDO {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long userId;
    private String username;
    private String loginType;
    private String loginIp;
    private String userAgent;
    private String status;
    private String message;
    private LocalDateTime loginTime;
}
