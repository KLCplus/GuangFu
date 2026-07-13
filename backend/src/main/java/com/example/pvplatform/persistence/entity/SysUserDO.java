package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUserDO {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private String phone;
    private String avatarUrl;
    private Long avatarFileId;
    private Integer gender;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Integer tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
