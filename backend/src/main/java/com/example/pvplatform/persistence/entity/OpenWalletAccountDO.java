package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("open_wallet_account")
public class OpenWalletAccountDO {
    @TableId(type = IdType.AUTO)
    private Long accountId;
    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozenBalance;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
