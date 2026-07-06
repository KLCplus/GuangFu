package com.example.pvplatform.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.FaceProperties;
import com.example.pvplatform.module.auth.dto.FaceStatusVO;
import com.example.pvplatform.module.auth.face.FaceRecognitionProvider;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.JwtTokenService;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class FaceAuthService {
    private final FaceRecognitionProvider faceProvider;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysFaceAuthMapper faceAuthMapper;
    private final JwtTokenService jwtTokenService;
    private final LoginLogService logService;
    private final FaceProperties faceProps;
    private final Path storageRoot;

    public FaceAuthService(FaceRecognitionProvider faceProvider,
                           SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysFaceAuthMapper faceAuthMapper, JwtTokenService jwtTokenService,
                           LoginLogService logService, FaceProperties faceProps) {
        this.faceProvider = faceProvider;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.faceAuthMapper = faceAuthMapper;
        this.jwtTokenService = jwtTokenService;
        this.logService = logService;
        this.faceProps = faceProps;
        this.storageRoot = Paths.get(faceProps.storageDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public void enroll(MultipartFile image) {
        Long userId = SecurityUtils.requireCurrentUserId();
        if (image == null || image.isEmpty()) throw new BusinessException(400, "图片不能为空");
        validateImageType(image);

        String providerName = faceProvider.name().toLowerCase().replace(" ", "");
        String entityId = "user_" + userId;

        FaceRecognitionProvider.EnrollResult result = faceProvider.enrollFace(image, entityId);

        // Upsert face auth record
        SysFaceAuthDO existing = faceAuthMapper.selectOne(
            Wrappers.<SysFaceAuthDO>lambdaQuery()
                .eq(SysFaceAuthDO::getUserId, userId)
                .eq(SysFaceAuthDO::getProvider, providerName)
                .last("LIMIT 1"));

        if (existing != null) {
            existing.setFaceFeatureId(result.faceId());
            existing.setEntityId(result.entityId());
            existing.setFaceDbName(isAliyun() ? faceProps.aliyun().faceDbName() : null);
            existing.setStatus(1);
            faceAuthMapper.updateById(existing);
        } else {
            SysFaceAuthDO auth = new SysFaceAuthDO();
            auth.setUserId(userId);
            auth.setProvider(providerName);
            auth.setFaceDbName(isAliyun() ? faceProps.aliyun().faceDbName() : null);
            auth.setEntityId(result.entityId());
            auth.setFaceFeatureId(result.faceId());
            auth.setStatus(1);
            faceAuthMapper.insert(auth);
        }
    }

    /**
     * Face login. The response field named score contains Aliyun Confidence (0-100).
     */
    public Map<String, Object> login(MultipartFile image) {
        if (image == null || image.isEmpty()) throw new BusinessException(400, "图片不能为空");
        validateImageType(image);

        String providerName = faceProvider.name().toLowerCase().replace(" ", "");

        if (isAliyun()) {
            // Aliyun flow: searchFace returns entityId -> map to userId
            FaceRecognitionProvider.SearchResult result = faceProvider.searchFace(image);
            String entityId = result.entityId();

            SysFaceAuthDO record = faceAuthMapper.selectOne(
                Wrappers.<SysFaceAuthDO>lambdaQuery()
                    .eq(SysFaceAuthDO::getProvider, providerName)
                    .eq(SysFaceAuthDO::getEntityId, entityId)
                    .eq(SysFaceAuthDO::getStatus, 1)
                    .last("LIMIT 1"));

            if (record == null) throw new BusinessException(401, "人脸识别失败");

            SysUserDO user = userMapper.selectById(record.getUserId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1)
                throw new BusinessException(401, "用户不存在或已禁用");

            List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
            int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
            String access = jwtTokenService.generateAccessToken(
                user.getUserId(), user.getUsername(), roles, version);
            String refresh = jwtTokenService.generateRefreshToken(user.getUserId(), version);

            logService.record(user.getUserId(), user.getUsername(), "FACE_LOGIN",
                null, null, "SUCCESS", "provider=aliyun score=" + result.score());

            LoginVO vo = LoginVO.of(access, jwtTokenService.getAccessExpirationSeconds(),
                refresh, jwtTokenService.getRefreshExpirationSeconds(),
                user.getUserId(), user.getUsername(), user.getNickname(), roles);
            Map<String, Object> withScore = new LinkedHashMap<>(Map.of(
                "token", vo.token(), "expiresIn", vo.expiresIn(),
                "refreshToken", vo.refreshToken(), "refreshExpiresIn", vo.refreshExpiresIn(),
                "score", result.score(), "userInfo", vo.userInfo()));
            return withScore;
        } else {
            // Local mock flow: extract + match
            String feature = faceProvider.extract(image);
            List<SysFaceAuthDO> records = faceAuthMapper.selectList(
                Wrappers.<SysFaceAuthDO>lambdaQuery().eq(SysFaceAuthDO::getStatus, 1));

            for (SysFaceAuthDO record : records) {
                if (faceProvider.match(feature, record.getFaceFeatureId())) {
                    SysUserDO user = userMapper.selectById(record.getUserId());
                    if (user == null || user.getStatus() == null || user.getStatus() != 1) continue;
                    List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
                    int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
                    String access = jwtTokenService.generateAccessToken(
                        user.getUserId(), user.getUsername(), roles, version);
                    String refresh = jwtTokenService.generateRefreshToken(user.getUserId(), version);
                    logService.record(user.getUserId(), user.getUsername(), "FACE_LOGIN",
                        null, null, "SUCCESS", "provider=mock");
                    LoginVO vo = LoginVO.of(access, jwtTokenService.getAccessExpirationSeconds(),
                        refresh, jwtTokenService.getRefreshExpirationSeconds(),
                        user.getUserId(), user.getUsername(), user.getNickname(), roles);
                    Map<String, Object> m = new LinkedHashMap<>(Map.of(
                        "token", vo.token(), "expiresIn", vo.expiresIn(),
                        "refreshToken", vo.refreshToken(), "refreshExpiresIn", vo.refreshExpiresIn(),
                        "score", 100.0, "userInfo", vo.userInfo()));
                    return m;
                }
            }
            logService.record(null, "unknown", "FACE_LOGIN", null, null, "FAIL", "人脸识别不匹配");
            throw new BusinessException(401, "人脸识别失败");
        }
    }

    @Transactional
    public void revoke() {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysFaceAuthDO record = faceAuthMapper.selectOne(
            Wrappers.<SysFaceAuthDO>lambdaQuery().eq(SysFaceAuthDO::getUserId, userId).last("LIMIT 1"));
        if (record == null) throw new BusinessException(404, "未录入人脸");
        record.setStatus(0);
        faceAuthMapper.updateById(record);
    }

    public FaceStatusVO status() {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysFaceAuthDO record = faceAuthMapper.selectOne(
            Wrappers.<SysFaceAuthDO>lambdaQuery().eq(SysFaceAuthDO::getUserId, userId).last("LIMIT 1"));
        return new FaceStatusVO(
            record != null && record.getStatus() != null && record.getStatus() == 1,
            record != null && record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
    }

    private boolean isAliyun() {
        return "aliyun".equalsIgnoreCase(faceProps.provider());
    }

    private void validateImageType(MultipartFile f) {
        String name = f.getOriginalFilename();
        if (name == null) throw new BusinessException(400, "文件名不能为空");
        String ext = name.toLowerCase();
        if (!(ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png")))
            throw new BusinessException(400, "仅支持 jpg/jpeg/png 格式");
        if (f.getSize() > 5 * 1024 * 1024)
            throw new BusinessException(400, "图片大小不超过5MB");
    }
}
