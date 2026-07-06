package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.persistence.entity.SysLoginLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysLoginLogMapper extends BaseMapper<SysLoginLogDO> {

    @Select("""
        <script>
        SELECT * FROM sys_login_log
        WHERE 1=1
        <if test="username != null and username != ''">
            AND username LIKE CONCAT('%', #{username}, '%')
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
        <if test="loginType != null and loginType != ''">
            AND login_type = #{loginType}
        </if>
        ORDER BY login_time DESC
        </script>
        """)
    IPage<SysLoginLogDO> selectPage(Page<SysLoginLogDO> page,
                                    @Param("username") String username,
                                    @Param("status") String status,
                                    @Param("loginType") String loginType);
}
