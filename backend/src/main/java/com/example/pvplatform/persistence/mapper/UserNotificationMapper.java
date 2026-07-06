package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pvplatform.persistence.entity.UserNotificationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserNotificationMapper extends BaseMapper<UserNotificationDO> {
    @Insert({
        "<script>",
        "INSERT INTO user_notification",
        "(user_id,title,content,notification_type,related_type,related_id,read_status,created_at) VALUES",
        "<foreach collection='rows' item='r' separator=','>",
        "(#{r.userId},#{r.title},#{r.content},#{r.notificationType},#{r.relatedType},",
        "#{r.relatedId},#{r.readStatus},#{r.createdAt})",
        "</foreach>",
        "</script>"
    })
    int batchInsert(@Param("rows") List<UserNotificationDO> rows);
}
