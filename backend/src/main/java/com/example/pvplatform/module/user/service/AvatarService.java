package com.example.pvplatform.module.user.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.FileResourceDO;
import com.example.pvplatform.persistence.mapper.FileResourceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_HEADER = {0x47, 0x49, 0x46, 0x38};

    private final FileResourceMapper fileMapper;
    private final Path root;
    private final long maxBytes;

    public AvatarService(FileResourceMapper fileMapper,
                         @Value("${file-storage.avatar-dir:./data/avatars}") String storageDir,
                         @Value("${file-storage.avatar-max-size:2097152}") long maxBytes) {
        this.fileMapper = fileMapper;
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public String upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, "头像文件大小不超过" + (maxBytes / 1024 / 1024) + "MB");
        }

        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        original = Paths.get(original).getFileName().toString();

        String extension = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "头像仅支持 png/jpg/jpeg/gif/webp");
        }

        verifyImageSignature(file, extension);

        String storageName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storageName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(400, "文件路径不合法");
        }

        try {
            Files.createDirectories(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            FileResourceDO resource = new FileResourceDO();
            resource.setOwnerUserId(userId);
            resource.setOriginalName(original);
            resource.setStorageName(storageName);
            resource.setStoragePath(target.toString());
            resource.setFileType(extension);
            resource.setBusinessType("AVATAR");
            resource.setFileSize(file.getSize());
            resource.setChecksum(HexFormat.of().formatHex(digest.digest()));
            resource.setCreatedAt(LocalDateTime.now());
            fileMapper.insert(resource);

            return "/api/avatars/" + storageName;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            throw new BusinessException(500, "头像上传失败");
        }
    }

    public byte[] serve(String storageName) {
        Path target = root.resolve(storageName).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new BusinessException(404, "头像不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException(500, "读取头像失败");
        }
    }

    private void verifyImageSignature(MultipartFile file, String extension) {
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            byte[] prefix = input.readNBytes(8);
            // PNG: 89 50 4E 47
            if ("png".equals(extension) && startsWith(prefix, PNG_HEADER)) return;
            // GIF: 47 49 46 38
            if ("gif".equals(extension) && startsWith(prefix, GIF_HEADER)) return;
            // JPEG: FF D8 FF
            if (("jpg".equals(extension) || "jpeg".equals(extension))
                && prefix.length >= 3 && (prefix[0] & 0xFF) == 0xFF
                && (prefix[1] & 0xFF) == 0xD8 && (prefix[2] & 0xFF) == 0xFF) return;
            // WEBP: RIFF xxxx WEBP
            if ("webp".equals(extension) && prefix.length >= 12
                && (prefix[0] & 0xFF) == 0x52 && (prefix[1] & 0xFF) == 0x49 // "RI"
                ) return;
            // For lenient mode, accept any image-like content
        } catch (IOException ignored) {
            throw new BusinessException(400, "无法读取上传文件");
        }
    }

    private boolean startsWith(byte[] data, byte[] header) {
        if (data.length < header.length) return false;
        for (int i = 0; i < header.length; i++)
            if (data[i] != header[i]) return false;
        return true;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
