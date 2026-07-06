package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_file")
public class ModelFileDO {
    @TableId(type = IdType.AUTO)
    private Long fileId;
    private Long modelId;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String checksum;
    private Integer status;
    private LocalDateTime createdAt;
}
