package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("open_recharge_order")
public class OpenRechargeOrderDO {
    @TableId(type = IdType.AUTO)
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long accountId;
    private BigDecimal amount;
    private String currency;
    private String channel;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
