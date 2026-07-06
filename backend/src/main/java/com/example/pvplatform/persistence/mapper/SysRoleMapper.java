package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRoleDO> {
    @Select("""
        SELECT r.role_code
        FROM sys_role r
        INNER JOIN sys_user_role ur ON ur.role_id = r.role_id
        WHERE ur.user_id = #{userId} AND r.status = 1
        ORDER BY r.role_id
        """)
    List<String> selectRoleCodesByUserId(Long userId);
}
