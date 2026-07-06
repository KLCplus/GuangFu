package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.persistence.entity.SysUserDO;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUserDO> {
    IPage<SysUserDO> selectUserPage(Page<SysUserDO> page,
                                    @Param("keyword") String keyword,
                                    @Param("status") Integer status,
                                    @Param("role") String role);
}
