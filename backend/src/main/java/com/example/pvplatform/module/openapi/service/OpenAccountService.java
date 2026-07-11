package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.vo.*;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ApiCallLogMapper;
import com.example.pvplatform.persistence.mapper.ApiKeyMapper;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OpenAccountService {
    private static final BigDecimal UNIT_PRICE = new BigDecimal("0.01");
    private final ApiKeyMapper apiKeyMapper;
    private final ApiCallLogMapper callLogMapper;
    private final ModelInfoMapper modelMapper;

    public OpenAccountService(ApiKeyMapper apiKeyMapper, ApiCallLogMapper callLogMapper,
                              ModelInfoMapper modelMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.callLogMapper = callLogMapper;
        this.modelMapper = modelMapper;
    }

    public MarketplaceTrialVO requestTrial(Long modelId) {
        ModelInfoDO model = modelMapper.selectOne(Wrappers.<ModelInfoDO>lambdaQuery()
            .eq(ModelInfoDO::getModelId, modelId)
            .eq(ModelInfoDO::getStatus, "ONLINE").last("LIMIT 1"));
        if (model == null) {
            throw new BusinessException(404, "模型不存在或未上线");
        }
        int quota = "NUMERIC".equalsIgnoreCase(model.getModelType()) ? 100 : 30;
        return new MarketplaceTrialVO(System.currentTimeMillis(), model.getModelId(), model.getModelName(),
            LocalDateTime.now().plusDays(7), quota);
    }

    public List<ApiEntitlementVO> entitlements() {
        Long userId = SecurityUtils.requireCurrentUserId();
        List<ApiKeyDO> keys = ownKeys(userId);
        List<ApiCallLogDO> logs = callLogMapper.selectList(Wrappers.<ApiCallLogDO>lambdaQuery()
            .eq(ApiCallLogDO::getUserId, userId));
        Map<Long, ModelInfoDO> models = logs.stream().map(ApiCallLogDO::getModelId).filter(Objects::nonNull)
            .distinct().map(modelMapper::selectById).filter(Objects::nonNull)
            .collect(Collectors.toMap(ModelInfoDO::getModelId, Function.identity(), (left, right) -> left));

        return keys.stream().map(key -> {
            List<ApiCallLogDO> keyLogs = logs.stream()
                .filter(log -> Objects.equals(log.getApiKeyId(), key.getApiKeyId())).toList();
            ModelInfoDO model = keyLogs.stream().map(ApiCallLogDO::getModelId).filter(Objects::nonNull)
                .map(models::get).filter(Objects::nonNull).findFirst().orElse(null);
            return new ApiEntitlementVO(key.getApiKeyId(), model == null ? null : model.getModelId(),
                model == null ? "全部开放模型" : model.getModelName(), key.getKeyName(),
                key.getDailyQuota(), (long) keyLogs.size(), key.getExpireTime(), key.getStatus());
        }).toList();
    }

    public WalletVO wallet() {
        Long userId = SecurityUtils.requireCurrentUserId();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<ApiCallLogDO> monthLogs = callLogMapper.selectList(Wrappers.<ApiCallLogDO>lambdaQuery()
            .eq(ApiCallLogDO::getUserId, userId)
            .ge(ApiCallLogDO::getRequestTime, monthStart)
            .orderByDesc(ApiCallLogDO::getRequestTime));
        BigDecimal monthlyCost = UNIT_PRICE.multiply(BigDecimal.valueOf(monthLogs.size()))
            .setScale(2, RoundingMode.HALF_UP);
        List<WalletRecordVO> records = monthLogs.stream().limit(20).map(log -> new WalletRecordVO(
            log.getLogId(), "CONSUME", UNIT_PRICE.negate(), "开放 API 调用扣费", log.getRequestTime()
        )).toList();
        return new WalletVO(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), monthlyCost, "CNY", records);
    }

    public List<OpenPlanVO> plans() {
        return List.of(
            new OpenPlanVO("TRIAL", "免费试用", 100, BigDecimal.ZERO.setScale(2), 7, "适合模型联调和小流量验证"),
            new OpenPlanVO("STANDARD", "标准套餐", 10000, new BigDecimal("99.00"), 30, "适合常规预测调用"),
            new OpenPlanVO("PRO", "专业套餐", 100000, new BigDecimal("699.00"), 30, "适合生产环境高频调用")
        );
    }

    public OpenAccountOverviewVO overview() {
        return new OpenAccountOverviewVO(wallet(), entitlements(), plans());
    }

    private List<ApiKeyDO> ownKeys(Long userId) {
        return apiKeyMapper.selectList(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getUserId, userId)
            .orderByDesc(ApiKeyDO::getCreatedAt));
    }
}
