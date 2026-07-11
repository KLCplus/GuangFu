package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.security.ApiKeyPrincipal;
import com.example.pvplatform.module.openapi.vo.OpenPredictVO;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.module.prediction.service.PredictionExecutionService;
import com.example.pvplatform.module.prediction.service.PredictionPersistenceService;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import com.example.pvplatform.persistence.mapper.UserStationPermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class OpenApiService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ModelInfoMapper modelMapper;
    private final PowerStationMapper stationMapper;
    private final UserStationPermissionMapper permissionMapper;
    private final PredictionPersistenceService persistenceService;
    private final PredictionExecutionService executionService;
    private final ApiCallLogService callLogService;

    public OpenApiService(ModelInfoMapper modelMapper, PowerStationMapper stationMapper,
                          UserStationPermissionMapper permissionMapper,
                          PredictionPersistenceService persistenceService,
                          PredictionExecutionService executionService,
                          ApiCallLogService callLogService) {
        this.modelMapper = modelMapper;
        this.stationMapper = stationMapper;
        this.permissionMapper = permissionMapper;
        this.persistenceService = persistenceService;
        this.executionService = executionService;
        this.callLogService = callLogService;
    }

    public OpenPredictVO predict(OpenPredictRequest request, HttpServletRequest httpRequest) {
        ApiKeyPrincipal principal = requirePrincipal();
        LocalDateTime started = LocalDateTime.now();
        ModelInfoDO model = null;
        PredictionTaskDO task = null;
        int status = 200;
        String error = null;
        try {
            model = modelMapper.selectOne(Wrappers.<ModelInfoDO>lambdaQuery()
                .and(q -> q.eq(ModelInfoDO::getServiceModelName, request.modelName())
                    .or().eq(ModelInfoDO::getModelCode, request.modelName()))
                .eq(ModelInfoDO::getStatus, "ONLINE").last("LIMIT 1"));
            if (model == null) {
                throw new BusinessException(400, "模型不存在或未上线");
            }
            validateStation(request.stationId(), principal.userId());
            List<ModelInputFrame> frames = convertAndValidate(request.input(), model);
            validateImages(request.inputImages(), frames);
            String start = frames.get(0).time().format(FORMATTER);
            String end = frames.get(frames.size() - 1).time().format(FORMATTER);
            task = persistenceService.createTaskWithSnapshotsForUser(principal.userId(),
                request.stationId(), model.getModelId(), "OPEN_API", start, end, frames);
            persistenceService.markRunning(task.getTaskId());
            ModelPredictResponse.Data data = executionService.execute(model, frames, request.inputImages());
            persistenceService.saveResultsAndMarkSuccess(task.getTaskId(), data,
                executionService.getLastInputTime(frames));
            return new OpenPredictVO(task.getTaskId(), task.getTaskNo(), "SUCCESS",
                data.modelName(), data.predictions(), data.costTime());
        } catch (BusinessException e) {
            status = e.getCode() >= 400 && e.getCode() <= 599 ? e.getCode() : 400;
            error = e.getMessage();
            if (task != null) {
                persistenceService.markFailed(task.getTaskId(), error);
            }
            throw e;
        } catch (Exception e) {
            status = 502;
            error = "模型服务暂不可用";
            if (task != null) {
                persistenceService.markFailed(task.getTaskId(), error);
            }
            throw new BusinessException(502, error);
        } finally {
            callLogService.save(principal.userId(), principal.apiKeyId(),
                model == null ? null : model.getModelId(), httpRequest.getRequestURI(),
                httpRequest.getMethod(), clientIp(httpRequest), started, status, error,
                "{\"modelName\":\"" + safe(request.modelName()) + "\",\"frameCount\":"
                    + (request.input() == null ? 0 : request.input().size()) + ",\"imageCount\":"
                    + (request.inputImages() == null ? 0 : request.inputImages().size()) + "}",
                task == null ? null : "{\"taskId\":" + task.getTaskId() + "}");
            httpRequest.setAttribute("OPEN_API_AUDITED", Boolean.TRUE);
        }
    }

    private List<ModelInputFrame> convertAndValidate(List<ModelPredictRequest.InputFrame> input,
                                                      ModelInfoDO model) {
        if (input == null || input.size() != 30) {
            throw new BusinessException(400, "输入必须包含 30 帧");
        }
        List<ModelInputFrame> frames;
        try {
            frames = input.stream().map(frame -> new ModelInputFrame(
                LocalDateTime.parse(frame.time(), FORMATTER), frame.power(),
                frame.temperature(), frame.irradiance())).toList();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(400, "输入时间格式不合法");
        }
        int interval = model.getInputFrameIntervalSeconds() == null
            ? 60 : model.getInputFrameIntervalSeconds();
        for (int i = 0; i < frames.size(); i++) {
            ModelInputFrame frame = frames.get(i);
            if (!Double.isFinite(frame.power()) || !Double.isFinite(frame.temperature())
                || !Double.isFinite(frame.irradiance())) {
                throw new BusinessException(400, "输入包含非法数值");
            }
            if (i > 0 && Duration.between(frames.get(i - 1).time(), frame.time()).toSeconds() != interval) {
                throw new BusinessException(400, "输入时间序列不连续");
            }
        }
        return frames;
    }


    private void validateImages(List<ModelPredictRequest.ImageFrame> images, List<ModelInputFrame> frames) {
        if (images == null || images.size() != 30) {
            throw new BusinessException(400, "图片输入必须包含 30 张");
        }
        for (int i = 0; i < images.size(); i++) {
            ModelPredictRequest.ImageFrame image = images.get(i);
            if (image.image() == null || image.image().isBlank()) {
                throw new BusinessException(400, "图片输入不能为空");
            }
            LocalDateTime imageTime;
            try {
                imageTime = LocalDateTime.parse(image.time(), FORMATTER);
            } catch (DateTimeParseException | NullPointerException e) {
                throw new BusinessException(400, "图片输入时间格式不合法");
            }
            if (!imageTime.equals(frames.get(i).time())) {
                throw new BusinessException(400, "图片时间必须与数值时间一一对应");
            }
            if (i > 0 && Duration.between(LocalDateTime.parse(images.get(i - 1).time(), FORMATTER), imageTime).toSeconds() != 60) {
                throw new BusinessException(400, "图片时间序列必须按 1 分钟间隔连续");
            }
        }
    }

    private void validateStation(Long stationId, Long userId) {
        if (stationId == null) {
            return;
        }
        PowerStationDO station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(400, "电站不存在");
        }
        if (userId.equals(station.getOwnerUserId())) {
            return;
        }
        long count = permissionMapper.selectCount(Wrappers.<UserStationPermissionDO>lambdaQuery()
            .eq(UserStationPermissionDO::getStationId, stationId)
            .eq(UserStationPermissionDO::getUserId, userId));
        if (count == 0) {
            throw new BusinessException(403, "无权访问该电站");
        }
    }

    private ApiKeyPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new BusinessException(401, "API Key 无效");
        }
        return principal;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
            : forwarded.split(",")[0].trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "");
    }
}
