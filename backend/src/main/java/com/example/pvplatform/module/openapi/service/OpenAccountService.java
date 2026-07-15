package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.vo.*;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.OpenRechargeOrderDO;
import com.example.pvplatform.persistence.entity.OpenWalletAccountDO;
import com.example.pvplatform.persistence.entity.OpenWalletRecordDO;
import com.example.pvplatform.persistence.mapper.ApiCallLogMapper;
import com.example.pvplatform.persistence.mapper.ApiKeyMapper;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.persistence.mapper.OpenRechargeOrderMapper;
import com.example.pvplatform.persistence.mapper.OpenWalletAccountMapper;
import com.example.pvplatform.persistence.mapper.OpenWalletRecordMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OpenAccountService {
    private static final BigDecimal API_CALL_PRICE = new BigDecimal("0.01");
    private static final String CURRENCY = "CNY";
    private final ApiKeyMapper apiKeyMapper;
    private final ApiCallLogMapper callLogMapper;
    private final ModelInfoMapper modelMapper;
    private final OpenWalletAccountMapper walletAccountMapper;
    private final OpenWalletRecordMapper walletRecordMapper;
    private final OpenRechargeOrderMapper rechargeOrderMapper;

    public OpenAccountService(ApiKeyMapper apiKeyMapper, ApiCallLogMapper callLogMapper,
                              ModelInfoMapper modelMapper, OpenWalletAccountMapper walletAccountMapper,
                              OpenWalletRecordMapper walletRecordMapper,
                              OpenRechargeOrderMapper rechargeOrderMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.callLogMapper = callLogMapper;
        this.modelMapper = modelMapper;
        this.walletAccountMapper = walletAccountMapper;
        this.walletRecordMapper = walletRecordMapper;
        this.rechargeOrderMapper = rechargeOrderMapper;
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

    @Transactional
    public WalletVO wallet() {
        Long userId = SecurityUtils.requireCurrentUserId();
        OpenWalletAccountDO account = ensureAccount(userId);
        return wallet(account);
    }

    @Transactional
    public RechargeOrderVO recharge(java.math.BigDecimal amount, String channel) {
        Long userId = SecurityUtils.requireCurrentUserId();
        BigDecimal normalized = normalizeAmount(amount);
        OpenWalletAccountDO account = ensureAccount(userId);
        String safeChannel = "MOCK";
        LocalDateTime now = LocalDateTime.now();

        OpenRechargeOrderDO order = new OpenRechargeOrderDO();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setAccountId(account.getAccountId());
        order.setAmount(normalized);
        order.setCurrency(CURRENCY);
        order.setChannel(safeChannel);
        order.setStatus("PAID");
        order.setPaidAt(now);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        rechargeOrderMapper.insert(order);

        account.setBalance(account.getBalance().add(normalized).setScale(2, RoundingMode.HALF_UP));
        account.setUpdatedAt(now);
        walletAccountMapper.updateById(account);
        insertRecord(account, order.getOrderNo(), "RECHARGE", normalized, "测试充值", "MOCK 测试入账，不产生真实扣款", now);

        return toRechargeOrderVO(order);
    }

    @Transactional
    public void requireApiCallBalance(Long userId) {
        if (userId == null) {
            return;
        }
        OpenWalletAccountDO account = ensureAccount(userId);
        if (account.getBalance().compareTo(API_CALL_PRICE) < 0) {
            throw new BusinessException(402, "钱包余额不足，请先充值");
        }
    }

    @Transactional
    public void chargeApiCall(Long userId, Long apiKeyId, Long modelId) {
        if (userId == null) {
            return;
        }
        OpenWalletAccountDO account = ensureAccount(userId);
        if (account.getBalance().compareTo(API_CALL_PRICE) < 0) {
            throw new BusinessException(402, "钱包余额不足，请先充值");
        }
        LocalDateTime now = LocalDateTime.now();
        account.setBalance(account.getBalance().subtract(API_CALL_PRICE).setScale(2, RoundingMode.HALF_UP));
        account.setUpdatedAt(now);
        walletAccountMapper.updateById(account);
        String title = modelId == null ? "开放 API 调用扣费" : "开放 API 调用扣费：模型 #" + modelId;
        String remark = apiKeyId == null ? null : "apiKeyId=" + apiKeyId;
        insertRecord(account, null, "CONSUME", API_CALL_PRICE.negate(), title, remark, now);
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

    private WalletVO wallet(OpenWalletAccountDO account) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<OpenWalletRecordDO> records = walletRecordMapper.selectList(Wrappers.<OpenWalletRecordDO>lambdaQuery()
            .eq(OpenWalletRecordDO::getAccountId, account.getAccountId())
            .orderByDesc(OpenWalletRecordDO::getCreatedAt)
            .last("LIMIT 20"));
        List<OpenWalletRecordDO> monthConsumes = walletRecordMapper.selectList(Wrappers.<OpenWalletRecordDO>lambdaQuery()
            .eq(OpenWalletRecordDO::getAccountId, account.getAccountId())
            .eq(OpenWalletRecordDO::getType, "CONSUME")
            .ge(OpenWalletRecordDO::getCreatedAt, monthStart));
        BigDecimal monthlyCost = monthConsumes.stream()
            .map(OpenWalletRecordDO::getAmount)
            .filter(Objects::nonNull)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        return new WalletVO(account.getBalance(), account.getFrozenBalance(), monthlyCost,
            account.getCurrency(), records.stream().map(this::toWalletRecordVO).toList());
    }

    private OpenWalletAccountDO ensureAccount(Long userId) {
        OpenWalletAccountDO existing = walletAccountMapper.selectOne(Wrappers.<OpenWalletAccountDO>lambdaQuery()
            .eq(OpenWalletAccountDO::getUserId, userId).last("LIMIT 1"));
        if (existing != null) {
            existing.setBalance(normalizeAmount(existing.getBalance()));
            existing.setFrozenBalance(normalizeAmount(existing.getFrozenBalance()));
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        OpenWalletAccountDO account = new OpenWalletAccountDO();
        account.setUserId(userId);
        account.setBalance(BigDecimal.ZERO.setScale(2));
        account.setFrozenBalance(BigDecimal.ZERO.setScale(2));
        account.setCurrency(CURRENCY);
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        walletAccountMapper.insert(account);
        return account;
    }

    private void insertRecord(OpenWalletAccountDO account, String orderNo, String type, BigDecimal amount,
                              String title, String remark, LocalDateTime now) {
        OpenWalletRecordDO record = new OpenWalletRecordDO();
        record.setUserId(account.getUserId());
        record.setAccountId(account.getAccountId());
        record.setOrderNo(orderNo);
        record.setType(type);
        record.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        record.setBalanceAfter(account.getBalance().setScale(2, RoundingMode.HALF_UP));
        record.setTitle(title);
        record.setRemark(remark);
        record.setCreatedAt(now);
        walletRecordMapper.insert(record);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String nextOrderNo() {
        return "R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private WalletRecordVO toWalletRecordVO(OpenWalletRecordDO row) {
        return new WalletRecordVO(row.getRecordId(), row.getOrderNo(), row.getType(), row.getAmount(),
            row.getBalanceAfter(), row.getTitle(), row.getRemark(), row.getCreatedAt());
    }

    private RechargeOrderVO toRechargeOrderVO(OpenRechargeOrderDO row) {
        return new RechargeOrderVO(row.getOrderId(), row.getOrderNo(), row.getAmount(), row.getCurrency(),
            row.getChannel(), row.getStatus(), row.getPaidAt(), row.getCreatedAt());
    }

    private List<ApiKeyDO> ownKeys(Long userId) {
        return apiKeyMapper.selectList(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getUserId, userId)
            .orderByDesc(ApiKeyDO::getCreatedAt));
    }
}
