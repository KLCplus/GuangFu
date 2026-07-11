package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("open_wallet_record")
public class OpenWalletRecordDO {
    @TableId(type = IdType.AUTO)
    private Long recordId;
    private Long userId;
    private Long accountId;
    private String orderNo;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String title;
    private String remark;
    private LocalDateTime createdAt;
}
