package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pvplatform.module.openapi.vo.ApiUsageByKeyVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageByModelVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageSummaryVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageTrendVO;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ApiCallLogMapper extends BaseMapper<ApiCallLogDO> {

    @Select("<script>" +
        "SELECT " +
        "  COUNT(*) as totalCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) as successCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failedCalls, " +
        "  CASE WHEN COUNT(*) > 0 THEN ROUND(SUM(CASE WHEN l.biz_status = 'SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) ELSE 0 END as successRate, " +
        "  COALESCE(CAST(AVG(l.cost_time_ms) AS SIGNED), 0) as avgCostTimeMs, " +
        "  COALESCE(SUM(l.input_tokens), 0) as inputTokens, " +
        "  COALESCE(SUM(l.output_tokens), 0) as outputTokens, " +
        "  COALESCE(SUM(l.total_tokens), 0) as totalTokens " +
        "FROM api_call_log l " +
        "WHERE l.user_id = #{userId} " +
        "  <if test='startTime != null'> AND l.request_time &gt;= #{startTime} </if>" +
        "  <if test='endTime != null'> AND l.request_time &lt;= #{endTime} </if>" +
        "  <if test='apiKeyId != null'> AND l.api_key_id = #{apiKeyId} </if>" +
        "  <if test='modelId != null'> AND l.model_id = #{modelId} </if>" +
        "</script>")
    ApiUsageSummaryVO selectUsageSummary(@Param("userId") Long userId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("apiKeyId") Long apiKeyId,
                                          @Param("modelId") Long modelId);

    @Select("<script>" +
        "SELECT " +
        "  DATE_FORMAT(l.request_time, #{dateFormat}) as timeBucket, " +
        "  COUNT(*) as totalCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) as successCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failedCalls, " +
        "  COALESCE(CAST(AVG(l.cost_time_ms) AS SIGNED), 0) as avgCostTimeMs, " +
        "  COALESCE(SUM(l.total_tokens), 0) as totalTokens " +
        "FROM api_call_log l " +
        "WHERE l.user_id = #{userId} " +
        "  <if test='startTime != null'> AND l.request_time &gt;= #{startTime} </if>" +
        "  <if test='endTime != null'> AND l.request_time &lt;= #{endTime} </if>" +
        "  <if test='apiKeyId != null'> AND l.api_key_id = #{apiKeyId} </if>" +
        "  <if test='modelId != null'> AND l.model_id = #{modelId} </if>" +
        "GROUP BY timeBucket " +
        "ORDER BY timeBucket " +
        "</script>")
    List<ApiUsageTrendVO> selectUsageTrend(@Param("userId") Long userId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("apiKeyId") Long apiKeyId,
                                            @Param("modelId") Long modelId,
                                            @Param("dateFormat") String dateFormat);

    @Select("<script>" +
        "SELECT " +
        "  l.model_id as modelId, " +
        "  COALESCE(m.model_name, '未知模型') as modelName, " +
        "  COUNT(*) as totalCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) as successCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failedCalls, " +
        "  COALESCE(CAST(AVG(l.cost_time_ms) AS SIGNED), 0) as avgCostTimeMs, " +
        "  COALESCE(SUM(l.total_tokens), 0) as totalTokens " +
        "FROM api_call_log l " +
        "LEFT JOIN model_info m ON l.model_id = m.model_id " +
        "WHERE l.user_id = #{userId} " +
        "  <if test='startTime != null'> AND l.request_time &gt;= #{startTime} </if>" +
        "  <if test='endTime != null'> AND l.request_time &lt;= #{endTime} </if>" +
        "  <if test='apiKeyId != null'> AND l.api_key_id = #{apiKeyId} </if>" +
        "GROUP BY l.model_id, m.model_name " +
        "ORDER BY totalCalls DESC " +
        "</script>")
    List<ApiUsageByModelVO> selectUsageByModel(@Param("userId") Long userId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("apiKeyId") Long apiKeyId);

    @Select("<script>" +
        "SELECT " +
        "  l.api_key_id as apiKeyId, " +
        "  k.key_name as keyName, " +
        "  k.api_key_prefix as apiKeyPrefix, " +
        "  COUNT(*) as totalCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) as successCalls, " +
        "  COALESCE(SUM(CASE WHEN l.biz_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failedCalls, " +
        "  COALESCE(CAST(AVG(l.cost_time_ms) AS SIGNED), 0) as avgCostTimeMs, " +
        "  COALESCE(SUM(l.total_tokens), 0) as totalTokens " +
        "FROM api_call_log l " +
        "LEFT JOIN api_key k ON l.api_key_id = k.api_key_id " +
        "WHERE l.user_id = #{userId} " +
        "  <if test='startTime != null'> AND l.request_time &gt;= #{startTime} </if>" +
        "  <if test='endTime != null'> AND l.request_time &lt;= #{endTime} </if>" +
        "  <if test='modelId != null'> AND l.model_id = #{modelId} </if>" +
        "GROUP BY l.api_key_id, k.key_name, k.api_key_prefix " +
        "ORDER BY totalCalls DESC " +
        "</script>")
    List<ApiUsageByKeyVO> selectUsageByKey(@Param("userId") Long userId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("modelId") Long modelId);
}
